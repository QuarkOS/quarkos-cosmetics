# Based on Quark's HiddenDesktop.ps1. Keep the desktop private and track the whole child job.
function Initialize-CosmeticsHiddenDesktop {
    if ('CosmeticsHiddenJob' -as [type]) { return }
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

public sealed class CosmeticsHiddenJob : IDisposable {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    struct StartupInfo {
        public int size;
        public string reserved, desktop, title;
        public int x, y, width, height, columns, rows, fill, flags;
        public short show, reservedSize;
        public IntPtr reservedData, stdin, stdout, stderr;
    }
    [StructLayout(LayoutKind.Sequential)]
    struct ProcessInfo { public IntPtr process, thread; public uint pid, tid; }
    [StructLayout(LayoutKind.Sequential)]
    struct BasicLimits {
        public long processTime, jobTime;
        public uint flags;
        public UIntPtr workingMin, workingMax;
        public uint activeProcessLimit;
        public UIntPtr affinity;
        public uint priority, schedulingClass;
    }
    [StructLayout(LayoutKind.Sequential)]
    struct ExtendedLimits {
        public BasicLimits basic;
        public ulong readOps, writeOps, otherOps, readBytes, writeBytes, otherBytes;
        public UIntPtr processLimit, jobLimit, peakProcess, peakJob;
    }
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern IntPtr CreateDesktopW(string name, IntPtr device, IntPtr devMode, uint flags, uint access, IntPtr security);
    [DllImport("user32.dll")]
    static extern bool CloseDesktop(IntPtr desktop);
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern bool GetUserObjectInformationW(IntPtr obj, int index, StringBuilder buffer, uint size, out uint required);
    delegate bool WindowCallback(IntPtr window, IntPtr arg);
    [DllImport("user32.dll")]
    static extern bool EnumDesktopWindows(IntPtr desktop, WindowCallback callback, IntPtr arg);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    static extern int GetWindowTextW(IntPtr window, StringBuilder title, int size);
    [DllImport("user32.dll")]
    static extern uint GetWindowThreadProcessId(IntPtr window, out uint pid);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern bool CreateProcessW(string app, StringBuilder cmd, IntPtr processAttrs, IntPtr threadAttrs,
        bool inherit, uint flags, IntPtr environment, string cwd, ref StartupInfo startup, out ProcessInfo process);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern IntPtr CreateJobObjectW(IntPtr attrs, string name);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool SetInformationJobObject(IntPtr job, int kind, ref ExtendedLimits data, uint length);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
    [DllImport("kernel32.dll")]
    static extern IntPtr OpenProcess(uint access, bool inherit, uint pid);
    [DllImport("kernel32.dll")]
    static extern bool IsProcessInJob(IntPtr process, IntPtr job, out bool belongs);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern uint ResumeThread(IntPtr thread);
    [DllImport("kernel32.dll")]
    static extern bool TerminateProcess(IntPtr process, uint code);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool GetExitCodeProcess(IntPtr process, out uint code);
    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr handle);

    IntPtr desktop, job, process;
    public uint Pid { get; private set; }
    public string Desktop { get; private set; }

    public CosmeticsHiddenJob(string desktopName, string command, string cwd) {
        ProcessInfo child = new ProcessInfo();
        try {
            desktop = CreateDesktopW(desktopName, IntPtr.Zero, IntPtr.Zero, 0, 0x10000000, IntPtr.Zero);
            Check(desktop != IntPtr.Zero, "CreateDesktop");
            var name = new StringBuilder(256);
            uint required;
            Check(GetUserObjectInformationW(desktop, 2, name, 512, out required), "GetUserObjectInformation");
            Desktop = name.ToString();
            if (Desktop != desktopName) throw new InvalidOperationException("Unexpected desktop " + Desktop);
            job = CreateJobObjectW(IntPtr.Zero, null);
            Check(job != IntPtr.Zero, "CreateJobObject");
            var limits = new ExtendedLimits();
            limits.basic.flags = 0x2000; // JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            Check(SetInformationJobObject(job, 9, ref limits, (uint)Marshal.SizeOf(typeof(ExtendedLimits))), "SetInformationJobObject");

            var startup = new StartupInfo();
            startup.size = Marshal.SizeOf(typeof(StartupInfo));
            startup.desktop = @"WinSta0\" + desktopName;
            startup.flags = 1; // STARTF_USESHOWWINDOW with SW_HIDE
            // Start suspended so every descendant is inside this job before it can run.
            Check(CreateProcessW(null, new StringBuilder(command), IntPtr.Zero, IntPtr.Zero, false,
                  0x14, IntPtr.Zero, cwd, ref startup, out child), "CreateProcess");
            process = child.process;
            Pid = child.pid;
            Check(AssignProcessToJobObject(job, process), "AssignProcessToJobObject");
            Check(ResumeThread(child.thread) != uint.MaxValue, "ResumeThread");
        } catch {
            if (process != IntPtr.Zero) TerminateProcess(process, 1);
            Dispose();
            throw;
        } finally {
            if (child.thread != IntPtr.Zero) CloseHandle(child.thread);
        }
    }

    public bool Wait(uint milliseconds) {
        uint result = WaitForSingleObject(process, milliseconds);
        Check(result == 0 || result == 258, "WaitForSingleObject");
        return result == 0;
    }

    public uint MinecraftWindowProcess() {
        uint client = 0;
        EnumDesktopWindows(desktop, (window, arg) => {
            var name = new StringBuilder(128);
            if (GetWindowTextW(window, name, name.Capacity) == 0 || !name.ToString().StartsWith("Minecraft")) return true;
            uint pid;
            GetWindowThreadProcessId(window, out pid);
            IntPtr handle = OpenProcess(0x1000, false, pid);
            if (handle == IntPtr.Zero) return true;
            try {
                bool belongs;
                if (IsProcessInJob(handle, job, out belongs) && belongs) client = pid;
            } finally { CloseHandle(handle); }
            return client == 0;
        }, IntPtr.Zero);
        return client;
    }

    public uint ExitCode() {
        uint code;
        Check(GetExitCodeProcess(process, out code), "GetExitCodeProcess");
        return code;
    }

    public void Dispose() {
        if (job != IntPtr.Zero) { CloseHandle(job); job = IntPtr.Zero; }
        if (process != IntPtr.Zero) { CloseHandle(process); process = IntPtr.Zero; }
        if (desktop != IntPtr.Zero) { CloseDesktop(desktop); desktop = IntPtr.Zero; }
    }

    static void Check(bool ok, string operation) {
        if (!ok) throw new Win32Exception(Marshal.GetLastWin32Error(), operation + " failed (" + Marshal.GetLastWin32Error() + ")");
    }
}
'@
}
