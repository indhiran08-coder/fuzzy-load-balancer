package com.fuzzybalancer.fuzzy;

import com.fuzzybalancer.fuzzy.membership.TriangularMF;
import com.fuzzybalancer.fuzzy.membership.TrapezoidalMF;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * MembershipFunctionTest — Unit tests for fuzzy membership functions.
 *
 * Tests verify mathematical correctness of:
 *   - TriangularMF: peak at center, zero at edges
 *   - TrapezoidalMF: flat top, zero at outer edges
 *
 * Uses AssertJ for fluent assertions (preferred over JUnit's assertEquals
 * for better failure messages and chaining).
 *
 * @DisplayName — Provides human-readable test descriptions in IDE/reports.
 * @ParameterizedTest — Runs the same test with multiple input sets.
 *   Reduces test code duplication for boundary condition testing.
 */
@DisplayName("Fuzzy Membership Functions")
class MembershipFunctionTest {

    // =========================================================================
    // TRIANGULAR MF TESTS
    // =========================================================================

    @Test
    @DisplayName("TriangularMF: peak point has membership 1.0")
    void triangularMF_peakHasFullMembership() {
        TriangularMF mf = new TriangularMF("Medium", 20, 50, 80);
        assertThat(mf.getMembership(50)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("TriangularMF: left edge has membership 0.0")
    void triangularMF_leftEdgeHasZeroMembership() {
        TriangularMF mf = new TriangularMF("Medium", 20, 50, 80);
        assertThat(mf.getMembership(20)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TriangularMF: right edge has membership 0.0")
    void triangularMF_rightEdgeHasZeroMembership() {
        TriangularMF mf = new TriangularMF("Medium", 20, 50, 80);
        assertThat(mf.getMembership(80)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TriangularMF: below left edge has membership 0.0")
    void triangularMF_belowLeftEdge_returnsZero() {
        TriangularMF mf = new TriangularMF("Medium", 20, 50, 80);
        assertThat(mf.getMembership(0)).isEqualTo(0.0);
        assertThat(mf.getMembership(10)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TriangularMF: above right edge has membership 0.0")
    void triangularMF_aboveRightEdge_returnsZero() {
        TriangularMF mf = new TriangularMF("Medium", 20, 50, 80);
        assertThat(mf.getMembership(100)).isEqualTo(0.0);
        assertThat(mf.getMembership(90)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TriangularMF: ascending slope is linear")
    void triangularMF_ascendingSlope_isLinear() {
        TriangularMF mf = new TriangularMF("Medium", 20, 50, 80);
        // At midpoint of ascending slope (20 to 50), x=35: (35-20)/(50-20) = 15/30 = 0.5
        assertThat(mf.getMembership(35)).isCloseTo(0.5, within(0.001));
    }

    @Test
    @DisplayName("TriangularMF: descending slope is linear")
    void triangularMF_descendingSlope_isLinear() {
        TriangularMF mf = new TriangularMF("Medium", 20, 50, 80);
        // At midpoint of descending slope (50 to 80), x=65: (80-65)/(80-50) = 15/30 = 0.5
        assertThat(mf.getMembership(65)).isCloseTo(0.5, within(0.001));
    }

    @Test
    @DisplayName("TriangularMF: all membership values are in [0, 1]")
    void triangularMF_allValues_inRange() {
        TriangularMF mf = new TriangularMF("Test", 10, 50, 90);
        for (int x = 0; x <= 100; x++) {
            double membership = mf.getMembership(x);
            assertThat(membership)
                .as("Membership at x=%d", x)
                .isBetween(0.0, 1.0);
        }
    }

    @ParameterizedTest(name = "TriangularMF CPU Medium: input={0} → expected={1}")
    @CsvSource({
        "0,   0.0",    // Below range
        "20,  0.0",    // Left edge
        "35,  0.5",    // Ascending
        "50,  1.0",    // Peak
        "65,  0.5",    // Descending
        "80,  0.0",    // Right edge
        "100, 0.0"     // Above range
    })
    @DisplayName("TriangularMF: CPU Medium membership function parametrized")
    void triangularMF_cpuMedium_parametrized(double input, double expected) {
        TriangularMF cpuMedium = new TriangularMF("Medium", 20, 50, 80);
        assertThat(cpuMedium.getMembership(input)).isCloseTo(expected, within(0.001));
    }

    @Test
    @DisplayName("TriangularMF: throws exception for invalid vertices (a > b)")
    void triangularMF_invalidVertices_throwsException() {
        assertThatThrownBy(() -> new TriangularMF("Invalid", 50, 20, 80))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("a <= b <= c");
    }

    // =========================================================================
    // TRAPEZOIDAL MF TESTS
    // =========================================================================

    @Test
    @DisplayName("TrapezoidalMF: flat top region has membership 1.0")
    void trapezoidalMF_flatTop_hasFullMembership() {
        TrapezoidalMF mf = new TrapezoidalMF("Low", 0, 0, 20, 40);
        assertThat(mf.getMembership(0)).isEqualTo(1.0);
        assertThat(mf.getMembership(10)).isEqualTo(1.0);
        assertThat(mf.getMembership(20)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("TrapezoidalMF: below left foot has membership 0.0")
    void trapezoidalMF_belowLeftFoot_returnsZero() {
        TrapezoidalMF mf = new TrapezoidalMF("High", 60, 80, 100, 100);
        assertThat(mf.getMembership(0)).isEqualTo(0.0);
        assertThat(mf.getMembership(60)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TrapezoidalMF: right foot (open right) has membership 1.0")
    void trapezoidalMF_openRight_hasFullMembership() {
        TrapezoidalMF mf = new TrapezoidalMF("High", 60, 80, 100, 100);
        assertThat(mf.getMembership(80)).isEqualTo(1.0);
        assertThat(mf.getMembership(90)).isEqualTo(1.0);
        // At exactly 100 (= d), membership = 0 (exclusive boundary)
    }

    @Test
    @DisplayName("TrapezoidalMF: ascending slope is linear")
    void trapezoidalMF_ascendingSlope_isLinear() {
        // CPU High: TrapezoidalMF(60, 80, 100, 100)
        // At x=70: (70-60)/(80-60) = 10/20 = 0.5
        TrapezoidalMF mf = new TrapezoidalMF("High", 60, 80, 100, 100);
        assertThat(mf.getMembership(70)).isCloseTo(0.5, within(0.001));
    }

    @ParameterizedTest(name = "TrapezoidalMF CPU Low: input={0} → expected={1}")
    @CsvSource({
        "0,   1.0",   // Flat top start
        "10,  1.0",   // Flat top middle
        "20,  1.0",   // Flat top end
        "30,  0.5",   // Descending slope midpoint
        "40,  0.0",   // Right foot
        "50,  0.0"    // Beyond range
    })
    @DisplayName("TrapezoidalMF: CPU Low membership function parametrized")
    void trapezoidalMF_cpuLow_parametrized(double input, double expected) {
        TrapezoidalMF cpuLow = new TrapezoidalMF("Low", 0, 0, 20, 40);
        assertThat(cpuLow.getMembership(input)).isCloseTo(expected, within(0.001));
    }

    @Test
    @DisplayName("TrapezoidalMF: all membership values are in [0, 1]")
    void trapezoidalMF_allValues_inRange() {
        TrapezoidalMF mf = new TrapezoidalMF("Test", 0, 20, 60, 80);
        for (int x = -10; x <= 100; x++) {
            double membership = mf.getMembership(x);
            assertThat(membership)
                .as("Membership at x=%d", x)
                .isBetween(0.0, 1.0);
        }
    }
}
