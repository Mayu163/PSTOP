using System;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading;

[assembly: AssemblyTitle("Pstop")]
[assembly: AssemblyDescription("Single-file launcher for the Pstop Windows resource monitor")]
[assembly: AssemblyCompany("Pstop contributors")]
[assembly: AssemblyProduct("Pstop")]
[assembly: AssemblyCopyright("Copyright © 2026 Pstop contributors")]
[assembly: AssemblyVersion("0.1.0.0")]
[assembly: AssemblyFileVersion("0.1.0.0")]

namespace Pstop.Standalone
{
    internal static class Program
    {
        private const string Version = "0.1.0";
        private const string PayloadResource = "Pstop.Payload.zip";
        private const string ReadyMarker = ".pstop-ready";

        [STAThread]
        private static int Main(string[] args)
        {
            try
            {
                string payloadHash = ComputePayloadHash();
                string cacheRoot = ResolveCacheRoot();
                string runtimeDirectory = Path.Combine(
                    cacheRoot,
                    Version + "-" + payloadHash.Substring(0, 16).ToLowerInvariant());

                EnsurePayload(runtimeDirectory, payloadHash);

                string executable = Path.Combine(runtimeDirectory, "Pstop.exe");
                if (!File.Exists(executable))
                {
                    throw new FileNotFoundException("The embedded Pstop runtime did not contain Pstop.exe.", executable);
                }

                ProcessStartInfo startInfo = new ProcessStartInfo();
                startInfo.FileName = executable;
                startInfo.Arguments = JoinArguments(args);
                startInfo.WorkingDirectory = Environment.CurrentDirectory;
                startInfo.UseShellExecute = false;
                startInfo.CreateNoWindow = false;

                using (Process process = Process.Start(startInfo))
                {
                    if (process == null)
                    {
                        throw new InvalidOperationException("Windows could not start the embedded Pstop runtime.");
                    }
                    process.WaitForExit();
                    return process.ExitCode;
                }
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine("Pstop could not start: " + exception.Message);
                return 1;
            }
        }

        private static string ResolveCacheRoot()
        {
            string overridePath = Environment.GetEnvironmentVariable("PSTOP_CACHE_DIR");
            if (!String.IsNullOrWhiteSpace(overridePath))
            {
                return Path.GetFullPath(overridePath);
            }

            string localApplicationData = Environment.GetFolderPath(
                Environment.SpecialFolder.LocalApplicationData);
            if (String.IsNullOrWhiteSpace(localApplicationData))
            {
                localApplicationData = Path.GetTempPath();
            }
            return Path.Combine(localApplicationData, "Pstop", "runtime");
        }

        private static string ComputePayloadHash()
        {
            using (Stream payload = OpenPayload())
            using (SHA256 sha256 = SHA256.Create())
            {
                byte[] hash = sha256.ComputeHash(payload);
                StringBuilder value = new StringBuilder(hash.Length * 2);
                foreach (byte item in hash)
                {
                    value.Append(item.ToString("x2", CultureInfo.InvariantCulture));
                }
                return value.ToString();
            }
        }

        private static Stream OpenPayload()
        {
            Stream payload = Assembly.GetExecutingAssembly().GetManifestResourceStream(PayloadResource);
            if (payload == null)
            {
                throw new InvalidOperationException("The embedded Pstop payload is missing.");
            }
            return payload;
        }

        private static void EnsurePayload(string runtimeDirectory, string payloadHash)
        {
            string mutexName = "Local\\PstopPayload_" + payloadHash.Substring(0, 24);
            using (Mutex mutex = new Mutex(false, mutexName))
            {
                bool ownsMutex = false;
                try
                {
                    try
                    {
                        ownsMutex = mutex.WaitOne(TimeSpan.FromMinutes(2));
                    }
                    catch (AbandonedMutexException)
                    {
                        ownsMutex = true;
                    }

                    if (!ownsMutex)
                    {
                        throw new TimeoutException("Timed out while another Pstop instance prepared the runtime.");
                    }

                    if (IsPayloadReady(runtimeDirectory, payloadHash))
                    {
                        return;
                    }

                    if (Directory.Exists(runtimeDirectory))
                    {
                        Directory.Delete(runtimeDirectory, true);
                    }

                    string parent = Path.GetDirectoryName(runtimeDirectory);
                    if (String.IsNullOrEmpty(parent))
                    {
                        throw new InvalidOperationException("The Pstop runtime cache path is invalid.");
                    }
                    Directory.CreateDirectory(parent);

                    string temporaryDirectory = runtimeDirectory + ".extracting-" +
                        Process.GetCurrentProcess().Id.ToString(CultureInfo.InvariantCulture) + "-" +
                        Guid.NewGuid().ToString("N");
                    try
                    {
                        ExtractPayload(temporaryDirectory);
                        File.WriteAllText(Path.Combine(temporaryDirectory, ReadyMarker), payloadHash);
                        Directory.Move(temporaryDirectory, runtimeDirectory);
                    }
                    finally
                    {
                        if (Directory.Exists(temporaryDirectory))
                        {
                            Directory.Delete(temporaryDirectory, true);
                        }
                    }
                }
                finally
                {
                    if (ownsMutex)
                    {
                        mutex.ReleaseMutex();
                    }
                }
            }
        }

        private static bool IsPayloadReady(string runtimeDirectory, string payloadHash)
        {
            string marker = Path.Combine(runtimeDirectory, ReadyMarker);
            if (!File.Exists(marker) ||
                !String.Equals(File.ReadAllText(marker).Trim(), payloadHash, StringComparison.Ordinal))
            {
                return false;
            }

            string[] requiredFiles =
            {
                Path.Combine(runtimeDirectory, "Pstop.exe"),
                Path.Combine(runtimeDirectory, "app", "Pstop.cfg"),
                Path.Combine(runtimeDirectory, "app", "pstop.jar"),
                Path.Combine(runtimeDirectory, "runtime", "bin", "server", "jvm.dll")
            };
            foreach (string requiredFile in requiredFiles)
            {
                if (!File.Exists(requiredFile))
                {
                    return false;
                }
            }
            return true;
        }

        private static void ExtractPayload(string destination)
        {
            Directory.CreateDirectory(destination);
            string root = Path.GetFullPath(destination).TrimEnd(
                Path.DirectorySeparatorChar,
                Path.AltDirectorySeparatorChar) + Path.DirectorySeparatorChar;

            using (Stream payload = OpenPayload())
            using (ZipArchive archive = new ZipArchive(payload, ZipArchiveMode.Read, false))
            {
                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    string relativePath = entry.FullName.Replace(
                        Path.AltDirectorySeparatorChar,
                        Path.DirectorySeparatorChar);
                    string target = Path.GetFullPath(Path.Combine(destination, relativePath));
                    if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase))
                    {
                        throw new InvalidDataException("The embedded payload contains an unsafe path.");
                    }

                    if (String.IsNullOrEmpty(entry.Name))
                    {
                        Directory.CreateDirectory(target);
                        continue;
                    }

                    string targetDirectory = Path.GetDirectoryName(target);
                    if (!String.IsNullOrEmpty(targetDirectory))
                    {
                        Directory.CreateDirectory(targetDirectory);
                    }

                    using (Stream input = entry.Open())
                    using (FileStream output = new FileStream(
                        target,
                        FileMode.CreateNew,
                        FileAccess.Write,
                        FileShare.None))
                    {
                        input.CopyTo(output);
                    }
                }
            }
        }

        private static string JoinArguments(string[] args)
        {
            if (args == null || args.Length == 0)
            {
                return String.Empty;
            }

            StringBuilder commandLine = new StringBuilder();
            for (int index = 0; index < args.Length; index++)
            {
                if (index > 0)
                {
                    commandLine.Append(' ');
                }
                commandLine.Append(QuoteArgument(args[index]));
            }
            return commandLine.ToString();
        }

        private static string QuoteArgument(string value)
        {
            if (value == null)
            {
                value = String.Empty;
            }
            if (value.Length > 0 && value.IndexOfAny(new[] { ' ', '\t', '\n', '\v', '"' }) < 0)
            {
                return value;
            }

            StringBuilder quoted = new StringBuilder();
            quoted.Append('"');
            int backslashes = 0;
            foreach (char character in value)
            {
                if (character == '\\')
                {
                    backslashes++;
                }
                else if (character == '"')
                {
                    quoted.Append('\\', backslashes * 2 + 1);
                    quoted.Append('"');
                    backslashes = 0;
                }
                else
                {
                    quoted.Append('\\', backslashes);
                    quoted.Append(character);
                    backslashes = 0;
                }
            }
            quoted.Append('\\', backslashes * 2);
            quoted.Append('"');
            return quoted.ToString();
        }
    }
}
