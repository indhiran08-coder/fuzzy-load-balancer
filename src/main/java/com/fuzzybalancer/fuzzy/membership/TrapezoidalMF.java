package com.fuzzybalancer.fuzzy.membership;

import lombok.Getter;

/**
 * TrapezoidalMF — Trapezoidal Membership Function.
 *
 * Defined by four points: (a, b, c, d).
 *   - From a to b: rising slope (0 → 1)
 *   - From b to c: flat top (membership = 1.0)
 *   - From c to d: falling slope (1 → 0)
 *
 * Shape:
 *        1.0 |      _________
 *            |     /         \
 *            |    /           \
 *        0.0 |___/             \___
 *                a    b    c    d
 *
 * Formula:
 *   μ(x) = 0              if x <= a or x >= d
 *           (x-a)/(b-a)   if a < x < b  (rising)
 *           1.0            if b <= x <= c  (flat top)
 *           (d-x)/(d-c)   if c < x < d  (falling)
 *
 * Best used for:
 *   - "Low" sets: trapezoid open on the left (a = b = 0)
 *   - "High" sets: trapezoid open on the right (c = d = max)
 *   - Any set with a range of "fully belonging" values
 *
 * Examples in our system:
 *
 * CPU "Low": TrapezoidalMF("Low", 0, 0, 20, 40)
 *   CPU = 0-20% → μ = 1.0 (fully Low)
 *   CPU = 30%   → μ = 0.5 (borderline Low)
 *   CPU = 40%   → μ = 0.0 (no longer Low)
 *
 * CPU "High": TrapezoidalMF("High", 60, 80, 100, 100)
 *   CPU = 60%   → μ = 0.0 (entering High)
 *   CPU = 80%   → μ = 1.0 (fully High)
 *   CPU = 100%  → μ = 1.0 (fully High)
 */
@Getter
public class TrapezoidalMF implements MembershipFunction {

    private final String name;
    /** Left foot — membership starts rising */
    private final double a;
    /** Left shoulder — membership reaches 1.0 */
    private final double b;
    /** Right shoulder — membership starts falling */
    private final double c;
    /** Right foot — membership drops to 0 */
    private final double d;

    /**
     * Constructor — validates a ≤ b ≤ c ≤ d.
     *
     * @param name Descriptive name (e.g., "Low", "High")
     * @param a    Left foot
     * @param b    Left shoulder
     * @param c    Right shoulder
     * @param d    Right foot
     */
    public TrapezoidalMF(String name, double a, double b, double c, double d) {
        if (a > b || b > c || c > d) {
            throw new IllegalArgumentException(
                String.format("TrapezoidalMF '%s': requires a <= b <= c <= d, got a=%.1f, b=%.1f, c=%.1f, d=%.1f",
                    name, a, b, c, d)
            );
        }
        this.name = name;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    /**
     * getMembership() — Computes trapezoidal membership degree.
     *
     * @param x Input crisp value
     * @return Membership degree in [0.0, 1.0]
     */
    @Override
    public double getMembership(double x) {
        if (x <= a || x >= d) {
            return 0.0;
        }

        if (x >= b && x <= c) {
            // Flat top region — fully belongs to this set
            return 1.0;
        }

        if (x > a && x < b) {
            // Rising slope: (x - a) / (b - a)
            return (b == a) ? 1.0 : (x - a) / (b - a);
        }

        if (x > c && x < d) {
            // Falling slope: (d - x) / (d - c)
            return (d == c) ? 1.0 : (d - x) / (d - c);
        }

        return 0.0;
    }

    @Override
    public String toString() {
        return String.format("TrapezoidalMF[%s](a=%.1f, b=%.1f, c=%.1f, d=%.1f)", name, a, b, c, d);
    }
}
