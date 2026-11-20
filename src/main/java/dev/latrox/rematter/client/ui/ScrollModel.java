package dev.latrox.rematter.client.ui;

/** A bounded row viewport; item identity and rendering remain with its owning screen. */
public final class ScrollModel {
    private int size, visibleRows, offset, selected = -1;
    private double wheelRemainder;

    public ScrollModel(int visibleRows) { setVisibleRows(visibleRows); }
    public int size() { return size; }
    public int visibleRows() { return visibleRows; }
    public int offset() { return offset; }
    public int maxOffset() { return Math.max(0, size - visibleRows); }
    public int end() { return Math.min(size, offset + visibleRows); }
    public int selected() { return selected; }
    public boolean scrollable() { return maxOffset() > 0; }

    public void setSize(int value) {
        size = Math.max(0, value);
        offset = Math.clamp(offset, 0, maxOffset());
        selected = size == 0 ? -1 : Math.clamp(selected, -1, size - 1);
    }
    public void setVisibleRows(int value) {
        visibleRows = Math.max(1, value);
        offset = Math.clamp(offset, 0, maxOffset());
    }
    public void setOffset(int value) { offset = Math.clamp(value, 0, maxOffset()); }
    public void scroll(int rows) { setOffset((int)Math.clamp((long)offset + rows, 0, maxOffset())); }
    public void reset() { offset = 0; selected = size == 0 ? -1 : 0; wheelRemainder = 0; }
    public void select(int index) {
        selected = size == 0 ? -1 : Math.clamp(index, 0, size - 1);
        if (selected < offset) offset = Math.max(0, selected);
        else if (selected >= offset + visibleRows) offset = selected - visibleRows + 1;
    }
    /** Three rows per wheel notch, while preserving small trackpad movements. */
    public void wheel(double movement) {
        if (!Double.isFinite(movement)) return;
        if (wheelRemainder * movement > 0) wheelRemainder = 0;
        wheelRemainder += -movement * 3;
        int rows = (int)Math.clamp(wheelRemainder, Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (rows != 0) {
            scroll(rows);
            wheelRemainder -= rows;
        }
    }
    /** GLFW key codes; no client runtime is needed to exercise viewport navigation. */
    public boolean key(int key) {
        switch (key) {
            case 265 -> select(selected < 0 ? 0 : selected - 1);
            case 264 -> select(selected < 0 ? 0 : selected + 1);
            case 266 -> select(Math.max(0, selected) - visibleRows);
            case 267 -> select(Math.max(0, selected) + visibleRows);
            case 268 -> select(0);
            case 269 -> select(size - 1);
            default -> { return false; }
        }
        return true;
    }
}
