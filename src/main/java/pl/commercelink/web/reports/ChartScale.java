package pl.commercelink.web.reports;

import java.util.List;

/** A value axis cut into four equal steps of a round size (1, 1.5, 2, 2.5 or 5 times a power of ten). */
public record ChartScale(double tick) {

    private static final double[] STEPS = {1, 1.5, 2, 2.5, 5, 10};

    public static ChartScale fitting(double max, boolean wholeNumbers) {
        if (!(max > 0)) {
            return new ChartScale(1);
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(max / 4)));
        for (double step : STEPS) {
            double tick = step * magnitude;
            if (wholeNumbers && (tick < 1 || tick != Math.floor(tick))) {
                continue;
            }
            if (tick * 4 >= max) {
                return new ChartScale(tick);
            }
        }
        return new ChartScale(10 * magnitude);
    }

    public double top() {
        return tick * 4;
    }

    public double percent(double value) {
        if (!(value > 0)) {
            return 0;
        }
        return Math.min(100, value / top() * 100);
    }

    public List<Double> ticks() {
        return List.of(0d, tick, tick * 2, tick * 3, tick * 4);
    }
}
