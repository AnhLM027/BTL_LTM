package Client.view;

/**
 * Lifecycle hook for a panel managed by {@link MainClientFrame}.
 */
public interface ClientScreen {
    default void onShow() {
    }

    default void onHide() {
    }
}
