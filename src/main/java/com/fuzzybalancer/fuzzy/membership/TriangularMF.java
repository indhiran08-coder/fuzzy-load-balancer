package com.fuzzybalancer.fuzzy.membership;

import lombok.Getter;

/**
 * TriangularMF — Triangular Membership Function.
 *
 * Defined by three points: (a, b, c) where b is the peak (membership = 1.0).
 *
 * Shape:
 *        1.0 |        /\
 *            |       /  \
 *            |      /    \
 *        0.0 |_____/      \______
 *                  a   b   c
 *
 * Formula:
 *   μ(x) = 0             if x <= a or x >= c
 *           (x-a)/(b-a)  if a < x <= b  (ascending slope)
 *           (c-x)/(c-b)  if b < x < c   (descending slope)
 *           1.0           if x == b      (peak)
 *
 * Best used for:
 *   - "Medium" sets where there's a clear center point
 *   - When the transition is gradual and symmetric
 *
 * Example — CPU "Medium" with TriangularMF(20, 45, 70):
 *   CPU = 20% → μ = 0.0  (just started entering Medium)
 *   CPU = 45% → μ = 1.0  (fully Medium)
 *   CPU = 70% → μ = 0.0  (just left Medium)
 *   CPU = 35% → μ = 0.6  (partially Medium)
 */
@Getter
public class TriangularMF implements MembershipFunction {

    private final String name;
    /** Left vertex — membership starts rising from 0 */
    private final double a;
    /** Peak vertex — maximum membership (1.0) */
    private final double b;
    /** Right vertex — membership drops back to 0 */
    private final double c;

    /**
     * Constructor — validates that a ≤ b ≤ c.
     *
     * @param name Descriptive name (e.g., "Medium")
     * @param a    Left vertex
     * @param b    Peak vertex
     * @param c    Right vertex
     */
    public TriangularMF(String name, double a, double b, double c) {
        if (a > b || b > c) {
            throw new IllegalArgumentException(
                String.format("TriangularMF '%s': requires a <= b <= c, got a=%.1f, b=%.1f, c=%.1f",
                    name, a, b, c)
            );
        }
        this.name = name;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    /**
     * getMembership() — Computes triangular membership degree.
     *
     * Edge cases handled:
     *   - a == b: left side is a vertical line (step up)
     *   - b == c: right side is a vertical line (step down)
     *   - a == b == c: membership is 1.0 only at exactly that point
     *
     * @param x Input crisp value
     * @return Membership degree in [0.0, 1.0]
     */
    @Override
    public double getMembership(double x) {
        if (x <= a || x >= c) {
            return 0.0;
        }

        if (x == b) {
            return 1.0;
        }

        if (x < b) {
            // Ascending slope: (x - a) / (b - a)
            // Guard against division by zero when a == b
            return (b == a) ? 1.0 : (x - a) / (b - a);
        } else {
            // Descending slope: (c - x) / (c - b)
            // Guard against division by zero when b == c
            return (c == b) ? 1.0 : (c - x) / (c - b);
        }
    }

    @Override
    public String toString() {
        return String.format("TriangularMF[%s](a=%.1f, b=%.1f, c=%.1f)", name, a, b, c);
    }
}
