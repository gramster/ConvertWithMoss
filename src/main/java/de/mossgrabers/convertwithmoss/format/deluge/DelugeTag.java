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

    /** Loop mode value: CUT - plays while note is held. */
    public static final String LOOP_MODE_CUT          = "0";

    /** Loop mode value: ONCE - plays through completely once. */
    public static final String LOOP_MODE_ONCE         = "1";

    /** Loop mode value: LOOP - loops continuously. */
    public static final String LOOP_MODE_LOOP         = "2";

    /** Loop mode value: STRETCH - time-stretches to song tempo. */
    public static final String LOOP_MODE_STRETCH      = "3";

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

    /** The maxVoices attribute. */
    public static final String MAX_VOICES             = "maxVoices";

    /** The osc2 tag. */
    public static final String OSC2                   = "osc2";

    /** The retrigPhase attribute. */
    public static final String RETRIG_PHASE           = "retrigPhase";

    /** The lfo1 tag. */
    public static final String LFO1                   = "lfo1";

    /** The lfo2 tag. */
    public static final String LFO2                   = "lfo2";

    /** The syncLevel attribute. */
    public static final String SYNC_LEVEL             = "syncLevel";

    /** The syncType attribute. */
    public static final String SYNC_TYPE              = "syncType";

    /** The unison tag. */
    public static final String UNISON                 = "unison";

    /** The num attribute (unison voices). */
    public static final String NUM                    = "num";

    /** The detune attribute. */
    public static final String DETUNE                 = "detune";

    /** The spread attribute. */
    public static final String SPREAD                 = "spread";

    /** The timeStretchEnable attribute. */
    public static final String TIME_STRETCH_ENABLE    = "timeStretchEnable";

    /** The timeStretchAmount attribute. */
    public static final String TIME_STRETCH_AMOUNT    = "timeStretchAmount";

    /** The envelope2 tag. */
    public static final String ENVELOPE2              = "envelope2";

    /** The patchCables tag. */
    public static final String PATCH_CABLES           = "patchCables";

    /** The patchCable tag. */
    public static final String PATCH_CABLE            = "patchCable";

    /** The source attribute. */
    public static final String SOURCE                 = "source";

    /** The destination attribute. */
    public static final String DESTINATION            = "destination";

    /** The amount attribute. */
    public static final String AMOUNT                 = "amount";

    /** The equalizer tag. */
    public static final String EQUALIZER              = "equalizer";

    /** The bass attribute. */
    public static final String BASS                   = "bass";

    /** The treble attribute. */
    public static final String TREBLE                 = "treble";

    /** The bassFrequency attribute. */
    public static final String BASS_FREQUENCY         = "bassFrequency";

    /** The trebleFrequency attribute. */
    public static final String TREBLE_FREQUENCY       = "trebleFrequency";

    /** The arpeggiator tag. */
    public static final String ARPEGGIATOR            = "arpeggiator";

    /** The mode attribute (used by arpeggiator). */
    public static final String MODE_ATTR              = "mode";

    /** The numOctaves attribute. */
    public static final String NUM_OCTAVES            = "numOctaves";

    /** The modKnobs tag. */
    public static final String MOD_KNOBS              = "modKnobs";

    /** The modKnob tag. */
    public static final String MOD_KNOB               = "modKnob";

    /** The controlsParam attribute. */
    public static final String CONTROLS_PARAM         = "controlsParam";

    /** The patchAmountFromSource attribute. */
    public static final String PATCH_AMOUNT_FROM_SOURCE = "patchAmountFromSource";

    /** The delay tag. */
    public static final String DELAY                  = "delay";

    /** The pingPong attribute. */
    public static final String PING_PONG              = "pingPong";

    /** The analog attribute. */
    public static final String ANALOG                 = "analog";

    /** The sidechain tag. */
    public static final String SIDECHAIN              = "sidechain";

    /** The audioCompressor tag. */
    public static final String AUDIO_COMPRESSOR       = "audioCompressor";

    /** The thresh attribute. */
    public static final String THRESH                 = "thresh";

    /** The ratio attribute. */
    public static final String RATIO                  = "ratio";

    /** The compHPF attribute. */
    public static final String COMP_HPF               = "compHPF";

    /** The compBlend attribute. */
    public static final String COMP_BLEND             = "compBlend";

    /** The arpeggiatorGate parameter. */
    public static final String ARPEGGIATOR_GATE       = "arpeggiatorGate";

    /** The portamento parameter. */
    public static final String PORTAMENTO             = "portamento";

    /** The compressorShape parameter. */
    public static final String COMPRESSOR_SHAPE       = "compressorShape";

    /** The oscAVolume parameter. */
    public static final String OSC_A_VOLUME           = "oscAVolume";

    /** The oscAPulseWidth parameter. */
    public static final String OSC_A_PULSE_WIDTH      = "oscAPulseWidth";

    /** The oscAWavetablePosition parameter. */
    public static final String OSC_A_WAVETABLE_POSITION = "oscAWavetablePosition";

    /** The oscBVolume parameter. */
    public static final String OSC_B_VOLUME           = "oscBVolume";

    /** The oscBPulseWidth parameter. */
    public static final String OSC_B_PULSE_WIDTH      = "oscBPulseWidth";

    /** The oscBWavetablePosition parameter. */
    public static final String OSC_B_WAVETABLE_POSITION = "oscBWavetablePosition";

    /** The noiseVolume parameter. */
    public static final String NOISE_VOLUME           = "noiseVolume";

    /** The lfo1Rate parameter. */
    public static final String LFO1_RATE              = "lfo1Rate";

    /** The lfo2Rate parameter. */
    public static final String LFO2_RATE              = "lfo2Rate";

    /** The modulator1Amount parameter. */
    public static final String MODULATOR1_AMOUNT      = "modulator1Amount";

    /** The modulator1Feedback parameter. */
    public static final String MODULATOR1_FEEDBACK    = "modulator1Feedback";

    /** The modulator2Amount parameter. */
    public static final String MODULATOR2_AMOUNT      = "modulator2Amount";

    /** The modulator2Feedback parameter. */
    public static final String MODULATOR2_FEEDBACK    = "modulator2Feedback";

    /** The carrier1Feedback parameter. */
    public static final String CARRIER1_FEEDBACK      = "carrier1Feedback";

    /** The carrier2Feedback parameter. */
    public static final String CARRIER2_FEEDBACK      = "carrier2Feedback";

    /** The modFXRate parameter. */
    public static final String MOD_FX_RATE            = "modFXRate";

    /** The modFXDepth parameter. */
    public static final String MOD_FX_DEPTH           = "modFXDepth";

    /** The delayRate parameter. */
    public static final String DELAY_RATE             = "delayRate";

    /** The delayFeedback parameter. */
    public static final String DELAY_FEEDBACK         = "delayFeedback";

    /** The reverbAmount parameter. */
    public static final String REVERB_AMOUNT          = "reverbAmount";

    /** The arpeggiatorRate parameter. */
    public static final String ARPEGGIATOR_RATE       = "arpeggiatorRate";

    /** The stutterRate parameter. */
    public static final String STUTTER_RATE           = "stutterRate";

    /** The sampleRateReduction parameter. */
    public static final String SAMPLE_RATE_REDUCTION  = "sampleRateReduction";

    /** The bitCrush parameter. */
    public static final String BIT_CRUSH              = "bitCrush";

    /** The modFXOffset parameter. */
    public static final String MOD_FX_OFFSET          = "modFXOffset";

    /** The modFXFeedback parameter. */
    public static final String MOD_FX_FEEDBACK        = "modFXFeedback";
    
    /**
     * Private constructor for utility class.
     */
    private DelugeTag ()
    {
        // Intentionally empty
    }
}