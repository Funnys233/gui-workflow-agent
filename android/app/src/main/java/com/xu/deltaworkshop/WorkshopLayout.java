package com.xu.deltaworkshop;

import java.util.ArrayList;
import java.util.List;

/** Locates the same workshop layout at different render sizes and safe-area offsets. */
final class WorkshopLayout {
    static final int WIDTH = 2712;
    static final int HEIGHT = 1220;
    private static final double[] SCALES = {1, 0.94, 1.06, 0.88, 1.12};

    record Frame(int width, int height, boolean[] ink, int left, int top) {
        Frame(int width, int height, int[] pixels) {
            this(width, height, new boolean[pixels.length], border(width,height,pixels,false), border(width,height,pixels,true));
            for (int i = 0; i < pixels.length; i++) ink[i] = light(pixels[i], 140, 85);
        }

        double scale() { return (double)(height - 2 * top) / HEIGHT; }

        private static int border(int width, int height, int[] pixels, boolean rows) {
            int limit = (rows ? height : width) / 5;
            int length = rows ? width : height;
            int side = rows ? height : width;
            for (int inset = 0; inset < limit; inset++) {
                for (int along = 0; along < length; along += 11) {
                    int first = rows ? inset * width + along : along * width + inset;
                    int last = rows ? (side - 1 - inset) * width + along : along * width + side - 1 - inset;
                    if (nonBlack(pixels[first]) || nonBlack(pixels[last])) return inset >= 4 ? inset : 0;
                }
            }
            return 0;
        }

        private static boolean nonBlack(int color) {
            return (color & 0x00f0f0f0) != 0;
        }
    }

    record Mark(int x, int y, double scale, double score) {}

    /** Maps reference coordinates to pixels in the original screenshot. */
    record Transform(double sx, double sy, double x, double y) {
        static Transform fromString(String value) {
            String[] fields = value.split(",", -1);
            if (fields.length != 4) return null;
            try {
                return new Transform(Double.parseDouble(fields[0]), Double.parseDouble(fields[1]),
                    Double.parseDouble(fields[2]), Double.parseDouble(fields[3]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        String encode() { return sx + "," + sy + "," + x + "," + y; }

        Transform centered(int frameWidth) {
            return new Transform(sx, sy, (frameWidth - WIDTH * sx) / 2, y);
        }

        Transform rightAligned(int frameWidth) {
            return new Transform(sx, sy, frameWidth - WIDTH * sx - x, y);
        }

        int mapX(int referenceX) { return (int)Math.round(x + sx * referenceX); }
        int mapY(int referenceY) { return (int)Math.round(y + sy * referenceY); }
    }

    private record Point(int x, int y) {}

    record Pattern(int x, int y, int width, int height, List<Point> bright, List<Point> dark) {
        Pattern(int x, int y, int width, int height, int[] pixels) {
            this(x, y, width, height, templatePoints(width, height, pixels, true),
                templatePoints(width, height, pixels, false));
        }

        private static List<Point> templatePoints(int width, int height, int[] pixels, boolean bright) {
            List<Point> points = new ArrayList<>();
            for (int y = 2; y < height - 2; y += 3) for (int x = 2; x < width - 2; x += 3) {
                int i = y * width + x;
                if (bright) {
                    if (light(pixels[i], 180, 60)) points.add(new Point(x, y));
                } else if (!light(pixels[i], 110, 255) &&
                    (light(pixels[i - 2], 180, 60) || light(pixels[i + 2], 180, 60) ||
                     light(pixels[i - 2 * width], 180, 60) || light(pixels[i + 2 * width], 180, 60))) {
                    points.add(new Point(x, y));
                }
            }
            int step = Math.max(1, (points.size() + 119) / 120);
            if (step == 1) return points;
            List<Point> reduced = new ArrayList<>();
            for (int i = 0; i < points.size(); i += step) reduced.add(points.get(i));
            return reduced;
        }
    }

    private record Samples(int width, int height, int[] bright, int[] dark) {
        Samples(Pattern p, int stride, double sx, double sy) {
            this((int)Math.ceil(p.width * sx), (int)Math.ceil(p.height * sy),
                offsets(p.bright, stride, sx, sy), offsets(p.dark, stride, sx, sy));
        }

        private static int[] offsets(List<Point> points, int stride, double sx, double sy) {
            int[] offsets = new int[points.size()];
            for (int i = 0; i < offsets.length; i++) {
                Point p = points.get(i);
                offsets[i] = (int)Math.round(p.y * sy) * stride + (int)Math.round(p.x * sx);
            }
            return offsets;
        }

        double score(Frame f, int x, int y) {
            if (bright.length < 12 || x < 0 || y < 0 || x + width > f.width || y + height > f.height) return 0;
            int start = y * f.width + x;
            int found = 0;
            for (int p : bright) if (f.ink[start + p]) found++;
            double letters = (double)found / bright.length;
            if (letters < .79) return 0;
            int clear = 0;
            for (int p : dark) if (!f.ink[start + p]) clear++;
            if (dark.length == 0) return letters;
            double gaps = (double)clear / dark.length;
            return gaps < .35 ? 0 : .8 * letters + .2 * gaps;
        }
    }

    static Mark locate(Frame f, Pattern p, double centerX, double centerY, double marginX, double marginY) {
        double base = f.scale();
        int left = Math.max(0, (int)Math.floor(centerX - marginX * base));
        int top = Math.max(0, (int)Math.floor(centerY - marginY * base));
        int step = Math.max(1, (int)Math.round(2 * base));
        Mark best = null;
        for (double factor : SCALES) {
            double scale = base * factor;
            Samples s = new Samples(p, f.width, scale, scale);
            int right = Math.min(f.width - s.width, (int)Math.ceil(centerX + marginX * base));
            int bottom = Math.min(f.height - s.height, (int)Math.ceil(centerY + marginY * base));
            Mark candidate = null;
            for (int y = top; y <= bottom; y += step) for (int x = left; x <= right; x += step) {
                double score = s.score(f, x, y);
                if (score > 0 && (candidate == null || score > candidate.score)) candidate = new Mark(x, y, scale, score);
            }
            if (candidate == null) continue;
            for (int y = candidate.y - step; y <= candidate.y + step; y++) {
                for (int x = candidate.x - step; x <= candidate.x + step; x++) {
                    double score = s.score(f, x, y);
                    if (best == null || score > best.score) best = new Mark(x, y, scale, score);
                }
            }
            if (best != null && best.score >= .93) break;
        }
        return best != null && best.score >= .80 ? best : null;
    }

    static Transform calibrate(Frame frame, Pattern[] anchors) {
        double base = frame.scale();
        Mark[] marks = new Mark[anchors.length];
        for (int i = 0; i < anchors.length; i++) {
            Pattern p = anchors[i];
            double fromLeft = i == 0 ? frame.left : marks[0].x - anchors[0].x * marks[0].scale;
            double fromTop = i == 0 ? frame.top : marks[0].y - anchors[0].y * marks[0].scale;
            double scale = i == 0 ? base : marks[0].scale;
            marks[i] = locate(frame, p, p.x * scale + fromLeft, p.y * scale + fromTop, 110, i == 0 ? 55 : 90);
            if (marks[i] == null) return null;
        }
        double sx = fitScale(anchors, marks, true), sy = fitScale(anchors, marks, false);
        double x = 0, y = 0;
        for (int i = 0; i < marks.length; i++) {
            x += marks[i].x - anchors[i].x * sx;
            y += marks[i].y - anchors[i].y * sy;
        }
        Transform fitted = new Transform(sx, sy, x / marks.length, y / marks.length);
        return valid(frame, anchors, fitted) ? fitted : null;
    }

    private static double fitScale(Pattern[] patterns, Mark[] marks, boolean horizontal) {
        double xx = 0, xy = 0, x = 0, y = 0;
        for (int i = 0; i < patterns.length; i++) {
            double ref = horizontal ? patterns[i].x : patterns[i].y;
            double found = horizontal ? marks[i].x : marks[i].y;
            xx += ref * ref;
            xy += ref * found;
            x += ref;
            y += found;
        }
        return (xy - x * y / patterns.length) / (xx - x * x / patterns.length);
    }

    static boolean valid(Frame frame, Pattern[] anchors, Transform transform) {
        if (transform == null || !Double.isFinite(transform.x) || !Double.isFinite(transform.y)) return false;
        double base = frame.scale();
        if (!(transform.sx >= .80 * base && transform.sx <= 1.20 * base &&
              transform.sy >= .80 * base && transform.sy <= 1.20 * base)) return false;
        for (Pattern anchor : anchors) {
            Samples samples = new Samples(anchor, frame.width, transform.sx, transform.sy);
            int x = (int)Math.round(anchor.x * transform.sx + transform.x);
            int y = (int)Math.round(anchor.y * transform.sy + transform.y);
            double best = 0;
            int tolerance = Math.max(1, (int)Math.round(2 * base));
            for (int dy = -tolerance; dy <= tolerance; dy++) for (int dx = -tolerance; dx <= tolerance; dx++)
                best = Math.max(best, samples.score(frame, x + dx, y + dy));
            if (best < .82) return false;
        }
        return true;
    }

    private static boolean light(int color, int floor, int spread) {
        int r=(color >>> 16) & 255, g=(color >>> 8) & 255, b=color & 255;
        int lo=Math.min(r, Math.min(g, b));
        return lo >= floor && Math.max(r, Math.max(g, b)) - lo < spread;
    }
}
