// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.deluge;

/**
 * All XML tags and attribute names used in the Deluge XML format.
 *
 * @author Jürgen Moßgraber
 */
public class DelugeTag
{
    /** The root tag. */
    public static final String SOUND                  = "sound";

    /** The osc1 tag. */
    public static final String OSC1                   = "osc1";

    /** The envelope1 tag. */
    public static final String ENVELOPE1              = "envelope1";

    /** The defaultParams tag. */
    public static final String DEFAULT_PARAMS         = "defaultParams";

    /** The sampleRange tag. */
    public static final String SAMPLE_RANGE           = "sampleRange";

    /** The sampleRanges tag. */
    public static final String SAMPLE_RANGES          = "sampleRanges";

    /** The zone tag. */
    public static final String ZONE                   = "zone";

    /** The type attribute. */
    public static final String TYPE                   = "type";

    /** The transpose attribute. */
    public static final String TRANSPOSE              = "transpose";

    /** The cents attribute. */
    public static final String CENTS                  = "cents";

    /** The fileName attribute. */
    public static final String FILE_NAME              = "fileName";

    /** The linearInterpolation attribute. */
    public static final String LINEAR_INTERPOLATION   = "linearInterpolation";

    /** The loopMode attribute. */
    public static final String LOOP_MODE              = "loopMode";

    /** The reversed attribute. */
    public static final String REVERSED               = "reversed";

    /** The rangeTopNote attribute. */
    public static final String RANGE_TOP_NOTE         = "rangeTopNote";

    /** The startSamplePos attribute. */
    public static final String START_SAMPLE_POS       = "startSamplePos";

    /** The endSamplePos attribute. */
    public static final String END_SAMPLE_POS         = "endSamplePos";

    /** The startLoopPos attribute. */
    public static final String START_LOOP_POS         = "startLoopPos";

    /** The endLoopPos attribute. */
    public static final String END_LOOP_POS           = "endLoopPos";

    /** The attack attribute. */
    public static final String ATTACK                 = "attack";

    /** The decay attribute. */
    public static final String DECAY                  = "decay";

    /** The sustain attribute. */
    public static final String SUSTAIN                = "sustain";

    /** The release attribute. */
    public static final String RELEASE                = "release";

    /** The firmwareVersion attribute. */
    public static final String FIRMWARE_VERSION       = "firmwareVersion";

    /** The earliestCompatibleFirmware attribute. */
    public static final String EARLIEST_COMPATIBLE_FIRMWARE = "earliestCompatibleFirmware";

    /** The polyphonic attribute. */
    public static final String POLYPHONIC             = "polyphonic";

    /** The voicePriority attribute. */
    public static final String VOICE_PRIORITY         = "voicePriority";

    /** The mode attribute. */
    public static final String MODE                   = "mode";

    /** The lpfMode attribute. */
    public static final String LPF_MODE               = "lpfMode";

    /** The hpfMode attribute. */
    public static final String HPF_MODE               = "hpfMode";

    /** The modFXType attribute. */
    public static final String MOD_FX_TYPE            = "modFXType";

    /** The filterRoute attribute. */
    public static final String FILTER_ROUTE           = "filterRoute";

    /** The lpfFrequency parameter. */
    public static final String LPF_FREQUENCY          = "lpfFrequency";

    /** The lpfResonance parameter. */
    public static final String LPF_RESONANCE          = "lpfResonance";

    /** The hpfFrequency parameter. */
    public static final String HPF_FREQUENCY          = "hpfFrequency";

    /** The hpfResonance parameter. */
    public static final String HPF_RESONANCE          = "hpfResonance";

    /** The volume parameter. */
    public static final String VOLUME                 = "volume";

    /** The pan parameter. */
    public static final String PAN                    = "pan";
    
    /**
     * Private constructor for utility class.
     */
    private DelugeTag ()
    {
        // Intentionally empty
    }
}