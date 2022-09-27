// ILoginSolver.aidl
package me.stageguard.aruku.service;

// Declare any non-default types here with import statements

interface ILoginSolver {
    String onSolvePicCaptcha(long bot, inout byte[] data);
    String onSolveSliderCaptcha(long bot, String url);
    String onSolveUnsafeDeviceLoginVerify(long bot, String url);
    void onLoginSuccess(long bot);
    void onLoginFailed(long bot, boolean botKilled, String cause);
}