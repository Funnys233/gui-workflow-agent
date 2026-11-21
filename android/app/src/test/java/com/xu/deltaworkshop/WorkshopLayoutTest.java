package com.xu.deltaworkshop;

import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorkshopLayoutTest {
    private static final int[][] ORIGINS = {{175,20}, {434,237}, {870,237}, {434,695}, {870,695}};
    private static final int BACKGROUND = 0x23262c;
    private final WorkshopLayout.Pattern[] anchors = new WorkshopLayout.Pattern[ORIGINS.length];

    private WorkshopLayout.Frame frame(int width, int height, int letterbox, int shift) {
        int[] screen = new int[width * height];
        Arrays.fill(screen, letterbox * width, (height - letterbox) * width, BACKGROUND);
        double scale = (height - 2.0 * letterbox) / WorkshopLayout.HEIGHT;
        for (int i = 0; i < anchors.length; i++) {
            int w = 108, h = 42, x = ORIGINS[i][0], y = ORIGINS[i][1];
            int[] pixels = new int[w * h];
            for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
                boolean stroke = (col / 8 + row / 8 * 3 + i) % 5 < 3 && col % 8 > 1 && row % 8 > 1;
                pixels[row * w + col] = stroke ? 0xffffff : BACKGROUND;
            }
            anchors[i] = new WorkshopLayout.Pattern(x, y, w, h, pixels);
            int left = shift + (int)Math.round(x * scale), top = letterbox + (int)Math.round(y * scale);
            for (int row = 0; row < (int)(h * scale); row++) for (int col = 0; col < (int)(w * scale); col++) {
                screen[(top + row) * width + left + col] = pixels[(int)(row / scale) * w + (int)(col / scale)];
            }
        }
        return new WorkshopLayout.Frame(width, height, screen);
    }

    @Test public void calibratesShiftedScreenAndRevalidatesCachedGeometry() {
        var first = frame(2608, 1200, 0, 21);
        var fit = WorkshopLayout.calibrate(first, anchors);
        assertNotNull(fit);
        assertEquals(21, fit.x(), 3);
        var saved = WorkshopLayout.Transform.fromString(fit.encode());
        assertTrue(WorkshopLayout.valid(first, anchors, saved));
        var moved = frame(2608, 1200, 0, 47);
        assertFalse(WorkshopLayout.valid(moved, anchors, saved));
        assertEquals(47, WorkshopLayout.calibrate(moved, anchors).x(), 3);
    }

    @Test public void findsActualGameAreaInsideBlackBars() {
        var frame = frame(1920, 1080, 108, 0);
        var fit = WorkshopLayout.calibrate(frame, anchors);
        assertNotNull(fit);
        assertEquals(108, fit.y(), 3);
        assertEquals(864.0 / 1220, fit.sy(), .01);
    }

    @Test public void mapsStationAndDialogControlsToTheirScreenEdges() {
        var overview = new WorkshopLayout.Transform(.98766, .98233, 18.27, .26);
        var dialog = overview.centered(2608);
        var stationRight = overview.rightAligned(2608);
        assertEquals(1304, dialog.mapX(1356));
        assertEquals(108, dialog.mapY(110));
        assertEquals(2232, stationRight.mapX(2350));
        assertEquals(1100, stationRight.mapY(1120));
    }

    @Test public void layoutVerificationRequiresACompletedRecipeCheck() throws Exception {
        var stations = new JSONArray();
        for (String name : MainActivity.STATIONS) {
            stations.put(new JSONObject().put("name", name).put("status", "already_running"));
        }
        var result = new JSONObject().put("ok", true).put("manufacturing", stations);
        assertFalse(RuntimeHost.verifiedRecipe(result));
        stations.getJSONObject(0).put("status", "quoted").put("quote", 41485);
        assertTrue(RuntimeHost.verifiedRecipe(result));
        result.put("ok", false);
        assertFalse(RuntimeHost.verifiedRecipe(result));
        result.put("ok", true);
        stations.getJSONObject(0).put("status", "materials_ready").remove("quote");
        assertTrue(RuntimeHost.verifiedRecipe(result));
    }
}
