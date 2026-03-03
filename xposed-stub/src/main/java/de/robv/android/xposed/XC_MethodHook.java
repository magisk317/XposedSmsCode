package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args = new Object[0];
        public boolean returnEarly;

        private Object result;
        private Throwable throwable;

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.returnEarly = true;
        }

        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) {
                throw throwable;
            }
            return result;
        }
    }

    public class Unhook {
        private final Member hookedMethod;

        public Unhook(Member hookedMethod) {
            this.hookedMethod = hookedMethod;
        }

        public Member getHookedMethod() {
            return hookedMethod;
        }

        public void unhook() {
            // no-op in compile-time stub
        }
    }
}
