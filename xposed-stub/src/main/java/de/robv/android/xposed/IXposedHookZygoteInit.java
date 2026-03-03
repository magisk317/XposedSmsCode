package de.robv.android.xposed;

public interface IXposedHookZygoteInit extends IXposedMod {
    void initZygote(StartupParam startupParam) throws Throwable;

    final class StartupParam {
        public String modulePath;
        public boolean startsSystemServer;
    }
}
