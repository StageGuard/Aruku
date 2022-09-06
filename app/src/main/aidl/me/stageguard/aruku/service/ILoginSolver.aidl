// ILoginSolver.aidl
package me.stageguard.aruku.service;

interface ILoginSolver {
    String onSolvePicCaptcha(long bot, out byte[] data);
    String onSolveSliderCaptcha(long bot, String url);
    String onSolveUnsafeDeviceLoginVerify(long bot, String url);
    void onLoginSuccess(long bot);
    void onLoginFailed(long bot, boolean botKilled, String cause);
}