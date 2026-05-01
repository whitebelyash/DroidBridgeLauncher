package ca.dnamobile.javalauncher.feature.unpack;

public abstract class AbstractUnpackTask implements Runnable {
    protected Listener listener;

    public interface Listener {
        default void onTaskStart() {
        }

        default void onTaskEnd() {
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public abstract boolean isNeedUnpack();
}
