// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.deluge;


/**
 * Converts between Deluge envelope hex values and time in seconds. The Deluge firmware uses lookup
 * tables to convert the stored 32-bit signed integer into a per-sample rate, which determines the
 * actual envelope time. Attack and decay/release use different tables.
 *
 * @author Jürgen Moßgraber
 */
class DelugeEnvelope
{
    /** Sample rate used by the Deluge for envelope calculations. */
    private static final double SAMPLE_RATE       = 44100.0;

    /** Amplitude constant used in rate-to-time conversion: time = AMPLITUDE / rate / SAMPLE_RATE */
    private static final double AMPLITUDE         = 8388608.0;

    /** Number of knob positions (0-50 inclusive). */
    private static final int    NUM_KNOB_POSITIONS = 50;

    /**
     * Attack rate lookup table from the Deluge firmware. Index 0 is the fastest attack (shortest
     * time), index 50 is the slowest (longest time). Values are descending.
     */
    private static final int [] ATTACK_RATE_TABLE  =
    {
        262144, 221969, 187951, 159147, 134757, 114105, 96618, 81811,
        69273, 58656, 49667, 42055, 35610, 30153, 25532, 21619,
        18306, 15500, 13125, 11113, 9410, 7968, 6747, 5713,
        4837, 4096, 3468, 2937, 2487, 2106, 1783, 1510,
        1278, 1082, 917, 776, 657, 556, 471, 399,
        338, 286, 242, 205, 174, 147, 124, 105,
        89, 76, 64
    };

    /**
     * Release/decay rate lookup table from the Deluge firmware. Index 0 is the fastest release
     * (shortest time), index 50 is the slowest (longest time). Values are descending.
     */
    private static final int [] RELEASE_RATE_TABLE =
    {
        32691, 4604, 2444, 1648, 1234, 980, 809, 685,
        592, 519, 460, 412, 372, 338, 309, 283,
        261, 241, 224, 208, 194, 181, 169, 159,
        149, 140, 132, 124, 117, 110, 104, 98,
        93, 88, 83, 78, 74, 70, 66, 62,
        59, 56, 53, 50, 47, 44, 41, 39,
        36, 34, 32
    };


    /**
     * Private constructor to prevent instantiation.
     */
    private DelugeEnvelope ()
    {
        // Intentionally empty
    }


    /**
     * Convert a Deluge hex string to attack time in seconds.
     *
     * @param hexValue The hex string from the XML (e.g. "0x18000000")
     * @return The attack time in seconds
     */
    static double hexToAttackTime (final String hexValue)
    {
        return rateToTime (interpolateTable (ATTACK_RATE_TABLE, hexToKnobPos (hexValue)));
    }


    /**
     * Convert a Deluge hex string to decay or release time in seconds.
     *
     * @param hexValue The hex string from the XML (e.g. "0xE6666654")
     * @return The decay/release time in seconds
     */
    static double hexToReleaseTime (final String hexValue)
    {
        return rateToTime (interpolateTable (RELEASE_RATE_TABLE, hexToKnobPos (hexValue)));
    }


    /**
     * Convert an attack time in seconds to a Deluge hex string.
     *
     * @param timeInSeconds The attack time in seconds
     * @return The hex string for the XML
     */
    static String attackTimeToHex (final double timeInSeconds)
    {
        final double rate = timeToRate (timeInSeconds);
        final double knobPos = reverseInterpolateTable (ATTACK_RATE_TABLE, rate);
        return knobPosToHex (knobPos);
    }


    /**
     * Convert a decay or release time in seconds to a Deluge hex string.
     *
     * @param timeInSeconds The decay/release time in seconds
     * @return The hex string for the XML
     */
    static String releaseTimeToHex (final double timeInSeconds)
    {
        final double rate = timeToRate (timeInSeconds);
        final double knobPos = reverseInterpolateTable (RELEASE_RATE_TABLE, rate);
        return knobPosToHex (knobPos);
    }


    /**
     * Convert a Deluge hex string to a normalized sustain value (0-1).
     *
     * @param hexValue The hex string from the XML
     * @return The sustain level (0.0 to 1.0)
     */
    static double hexToSustainLevel (final String hexValue)
    {
        final long signed = parseHexToSigned32 (hexValue);
        return (signed + 2147483648.0) / 4294967296.0;
    }


    /**
     * Convert a normalized sustain level (0-1) to a Deluge hex string.
     *
     * @param level The sustain level (0.0 to 1.0)
     * @return The hex string for the XML
     */
    static String sustainLevelToHex (final double level)
    {
        final double clamped = Math.clamp (level, 0.0, 1.0);
        final long signed = Math.round (clamped * 4294967296.0 - 2147483648.0);
        return String.format ("0x%08X", Long.valueOf (signed & 0xFFFFFFFFL));
    }


    /**
     * Parse a hex string to a signed 32-bit value.
     *
     * @param hexValue The hex string (e.g. "0x18000000")
     * @return The signed 32-bit value
     */
    private static long parseHexToSigned32 (final String hexValue)
    {
        if (hexValue == null || hexValue.isBlank ())
            return -2147483648L;

        try
        {
            long value = Long.parseLong (hexValue.replace ("0x", "").replace ("0X", ""), 16);
            if ((value & 0x80000000L) != 0)
                value -= 0x100000000L;
            return value;
        }
        catch (final NumberFormatException ex)
        {
            return -2147483648L;
        }
    }


    /**
     * Convert a hex string to a fractional knob position (0.0 to 50.0).
     *
     * @param hexValue The hex string
     * @return The fractional knob position
     */
    private static double hexToKnobPos (final String hexValue)
    {
        final long signed = parseHexToSigned32 (hexValue);
        return (signed + 2147483648.0) * NUM_KNOB_POSITIONS / 4294967296.0;
    }


    /**
     * Convert a knob position (0.0 to 50.0) to a Deluge hex string.
     *
     * @param knobPos The fractional knob position
     * @return The hex string
     */
    private static String knobPosToHex (final double knobPos)
    {
        final double clamped = Math.clamp (knobPos, 0.0, NUM_KNOB_POSITIONS);
        final long signed = Math.round (clamped * 4294967296.0 / NUM_KNOB_POSITIONS - 2147483648.0);
        return String.format ("0x%08X", Long.valueOf (signed & 0xFFFFFFFFL));
    }


    /**
     * Interpolate a rate value from the lookup table at a fractional index.
     *
     * @param table The lookup table (descending values)
     * @param pos The fractional index (0.0 to 50.0)
     * @return The interpolated rate value
     */
    private static double interpolateTable (final int [] table, final double pos)
    {
        final int idx = Math.clamp ((int) pos, 0, NUM_KNOB_POSITIONS - 1);
        final double frac = pos - idx;
        return table[idx] + frac * (table[idx + 1] - table[idx]);
    }


    /**
     * Reverse-interpolate: find the fractional table index for a given rate. The tables are
     * descending, so a higher rate means a lower index (faster time).
     *
     * @param table The lookup table (descending values)
     * @param rate The rate to look up
     * @return The fractional knob position (0.0 to 50.0)
     */
    private static double reverseInterpolateTable (final int [] table, final double rate)
    {
        // Rate above the table maximum means index 0 (fastest)
        if (rate >= table[0])
            return 0.0;
        // Rate below the table minimum means index 50 (slowest)
        if (rate <= table[NUM_KNOB_POSITIONS])
            return NUM_KNOB_POSITIONS;

        // Find the two adjacent entries that bracket the rate
        for (int i = 0; i < NUM_KNOB_POSITIONS; i++)
        {
            if (rate <= table[i] && rate >= table[i + 1])
            {
                final double frac = (double) (table[i] - rate) / (table[i] - table[i + 1]);
                return i + frac;
            }
        }

        return NUM_KNOB_POSITIONS;
    }


    /**
     * Convert a per-sample rate to time in seconds.
     *
     * @param rate The per-sample rate from the lookup table
     * @return The time in seconds
     */
    private static double rateToTime (final double rate)
    {
        if (rate <= 0)
            return AMPLITUDE / 1.0 / SAMPLE_RATE;
        return AMPLITUDE / rate / SAMPLE_RATE;
    }


    /**
     * Convert a time in seconds to a per-sample rate.
     *
     * @param timeInSeconds The time in seconds
     * @return The rate value
     */
    private static double timeToRate (final double timeInSeconds)
    {
        if (timeInSeconds <= 0)
            return AMPLITUDE; // Instant
        return AMPLITUDE / (timeInSeconds * SAMPLE_RATE);
    }
}
