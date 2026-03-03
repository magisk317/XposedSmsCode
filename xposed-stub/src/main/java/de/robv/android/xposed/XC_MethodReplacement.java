package de.robv.android.xposed;

public abstract class XC_MethodReplacement extends XC_MethodHook {

    @Override
    protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {
        param.setResult(replaceHookedMethod(param));
    }

    @Override
    protected final void afterHookedMethod(MethodHookParam param) {
        // no-op
    }

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;

    public static XC_MethodReplacement returnConstant(final Object result) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return result;
            }
        };
    }
}
