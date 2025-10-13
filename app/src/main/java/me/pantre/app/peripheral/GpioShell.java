package me.pantre.app.peripheral;

import android.annotation.SuppressLint;

import com.jaredrummler.ktsh.Shell;

public class GpioShell {
    public GpioShell() {
        shellRun("su");
    }

    private void shellRun(String command) {
        try {
            shell.run(command);
        } catch (Shell.ClosedException e) {
            e.printStackTrace();
        }
    }

    private final Shell shell = new Shell("sh");

    @SuppressLint("DefaultLocale")
    public void setGpioValue(final int chip, final int line, final int value) {
        shellRun(String.format("/system/bin/gpioset gpiochip%d %d=%d", chip, line, value));
    }
}
