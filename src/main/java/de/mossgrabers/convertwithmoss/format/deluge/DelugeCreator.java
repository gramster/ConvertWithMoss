// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.deluge;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.tools.XMLUtils;


/**
 * Creator for Synthstrom Deluge multi-sample files. A multi-sample consists of a description file
 * encoded in XML located in the SYNTHS folder and the related samples in the SAMPLES folder.
 *
 * @author Jürgen Moßgraber
 */
public class DelugeCreator extends AbstractWavCreator<DelugeCreatorUI>
{
    private static final DestinationAudioFormat OPTIMIZED_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, 44100, true);
    private static final DestinationAudioFormat DEFAULT_AUDIO_FORMAT   = new DestinationAudioFormat ();

    /** Maximum envelope time in seconds, must match the constant in DelugeDetector. */
    private static final double                 MAX_ENVELOPE_TIME      = 20.0;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DelugeCreator (final INotifier notifier)
    {
        super ("Deluge", "Deluge", notifier, new DelugeCreatorUI ("Deluge"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final boolean resample = this.settingsConfiguration.limitTo16441 ();
        final boolean trim = this.settingsConfiguration.trimStartToEnd ();
        final String subfolder = this.settingsConfiguration.getSamplesSubfolder ();

        // Create the SYNTHS and SAMPLES folders
        final File synthsFolder = new File (destinationFolder, "SYNTHS");
        safeCreateDirectory (synthsFolder);

        final File samplesFolder = new File (destinationFolder, "SAMPLES");
        safeCreateDirectory (samplesFolder);

        // Create a safe filename for the multisample
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Build the path under SAMPLES: optionally with subfolder
        final File instrumentSamplesFolder;
        final String samplesRelativePath;
        if (subfolder.isEmpty ())
        {
            instrumentSamplesFolder = new File (samplesFolder, sampleName);
            samplesRelativePath = "SAMPLES/" + sampleName;
        }
        else
        {
            final File subfolderDir = new File (samplesFolder, subfolder);
            safeCreateDirectory (subfolderDir);
            instrumentSamplesFolder = new File (subfolderDir, sampleName);
            samplesRelativePath = "SAMPLES/" + subfolder + "/" + sampleName;
        }
        safeCreateDirectory (instrumentSamplesFolder);

        // Create the XML file
        final Optional<String> metadata = this.createMetadata (sampleName, multisampleSource, trim, samplesRelativePath);
        if (metadata.isEmpty ())
            return;

        final File xmlFile = new File (synthsFolder, sampleName + ".XML");
        this.notifier.log ("IDS_NOTIFY_STORING", xmlFile.getAbsolutePath ());

        storeXML (xmlFile, metadata.get ());

        // Store all samples in the instrument's subfolder under SAMPLES
        if (resample)
            recalculateSamplePositions (multisampleSource, 44100);
        this.writeSamples (instrumentSamplesFolder, multisampleSource, resample ? OPTIMIZED_AUDIO_FORMAT : DEFAULT_AUDIO_FORMAT, trim);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create the XML file.
     *
     * @param xmlFile The file to store the XML
     * @param metadata The XML content
     * @throws IOException Could not store the file
     */
    private static void storeXML (final File xmlFile, final String metadata) throws IOException
    {
        try (final FileWriter writer = new FileWriter (xmlFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }
    }


    /**
     * Create the text of the XML file.
     *
     * @param instrumentName The name of the instrument
     * @param multisampleSource The multi-sample
     * @param trim Trim to start/end if true
     * @param samplesRelativePath The relative path from SD card root to the sample folder (e.g.
     *            "SAMPLES/subfolder/instrumentName")
     * @return The XML structure
     */
    private Optional<String> createMetadata (final String instrumentName, final IMultisampleSource multisampleSource, final boolean trim, final String samplesRelativePath)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        // Create the root element
        final Element rootElement = document.createElement (DelugeTag.SOUND);
        document.appendChild (rootElement);

        // Set basic attributes
        rootElement.setAttribute (DelugeTag.FIRMWARE_VERSION, "4.1.4-alpha");
        rootElement.setAttribute (DelugeTag.EARLIEST_COMPATIBLE_FIRMWARE, "4.1.0-alpha");
        rootElement.setAttribute (DelugeTag.POLYPHONIC, "poly");
        rootElement.setAttribute (DelugeTag.VOICE_PRIORITY, "1");
        rootElement.setAttribute (DelugeTag.MODE, "subtractive");
        rootElement.setAttribute (DelugeTag.LPF_MODE, "24dB");
        rootElement.setAttribute (DelugeTag.HPF_MODE, "HPLadder");
        rootElement.setAttribute (DelugeTag.MOD_FX_TYPE, "none");
        rootElement.setAttribute (DelugeTag.FILTER_ROUTE, "H2L");
        rootElement.setAttribute (DelugeTag.MAX_VOICES, "8");

        // Determine if any zone has loops
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        boolean hasAnyLoops = false;
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
                if (!zone.getLoops ().isEmpty ())
                {
                    hasAnyLoops = true;
                    break;
                }

        // Create the osc1 element which will contain the samples
        final Element osc1Element = XMLUtils.addElement (document, rootElement, DelugeTag.OSC1);
        osc1Element.setAttribute (DelugeTag.TYPE, "sample");
        osc1Element.setAttribute (DelugeTag.LOOP_MODE, hasAnyLoops ? DelugeTag.LOOP_MODE_LOOP : DelugeTag.LOOP_MODE_CUT);
        osc1Element.setAttribute (DelugeTag.REVERSED, "0");
        osc1Element.setAttribute (DelugeTag.TIME_STRETCH_ENABLE, "0");
        osc1Element.setAttribute (DelugeTag.TIME_STRETCH_AMOUNT, "0");

        // Create the sampleRanges element
        final Element sampleRangesElement = XMLUtils.addElement (document, osc1Element, DelugeTag.SAMPLE_RANGES);

        // Resolve unset root keys from sample metadata before flattening
        resolveRootKeys (groups);

        // Flatten all zones into a single layer with unique key ranges
        final List<ISampleZone> flattenedZones = flattenToSingleLayer (groups);
        for (final ISampleZone zone: flattenedZones)
            createSampleRange (document, sampleRangesElement, zone, trim, samplesRelativePath);

        // Create osc2 (default square oscillator, silent)
        final Element osc2Element = XMLUtils.addElement (document, rootElement, DelugeTag.OSC2);
        osc2Element.setAttribute (DelugeTag.TYPE, "square");
        osc2Element.setAttribute (DelugeTag.TRANSPOSE, "0");
        osc2Element.setAttribute (DelugeTag.CENTS, "0");
        osc2Element.setAttribute (DelugeTag.RETRIG_PHASE, "-1");

        // Create LFOs
        final Element lfo1Element = XMLUtils.addElement (document, rootElement, DelugeTag.LFO1);
        lfo1Element.setAttribute (DelugeTag.TYPE, "triangle");
        lfo1Element.setAttribute (DelugeTag.SYNC_LEVEL, "0");
        lfo1Element.setAttribute (DelugeTag.SYNC_TYPE, "0");
        final Element lfo2Element = XMLUtils.addElement (document, rootElement, DelugeTag.LFO2);
        lfo2Element.setAttribute (DelugeTag.TYPE, "triangle");
        lfo2Element.setAttribute (DelugeTag.SYNC_LEVEL, "0");
        lfo2Element.setAttribute (DelugeTag.SYNC_TYPE, "0");

        // Create unison
        final Element unisonElement = XMLUtils.addElement (document, rootElement, DelugeTag.UNISON);
        unisonElement.setAttribute (DelugeTag.NUM, "1");
        unisonElement.setAttribute (DelugeTag.DETUNE, "8");
        unisonElement.setAttribute (DelugeTag.SPREAD, "0");

        // Create default parameters
        final Element defaultParamsElement = XMLUtils.addElement (document, rootElement, DelugeTag.DEFAULT_PARAMS);
        setDefaultParams (defaultParamsElement);

        // Set filter parameters
        final Optional<IFilter> optFilter = multisampleSource.getGlobalFilter ();
        if (optFilter.isPresent ())
        {
            final IFilter filter = optFilter.get ();
            final FilterType type = filter.getType ();

            if (type == FilterType.LOW_PASS)
            {
                final double normalizedFrequency = MathUtils.normalizeFrequency (filter.getCutoff (), IFilter.MAX_FREQUENCY);
                defaultParamsElement.setAttribute (DelugeTag.LPF_FREQUENCY, convertToHex (normalizedFrequency));
                defaultParamsElement.setAttribute (DelugeTag.LPF_RESONANCE, convertToHex (filter.getResonance ()));
            }
            else if (type == FilterType.HIGH_PASS)
            {
                final double normalizedFrequency = MathUtils.normalizeFrequency (filter.getCutoff (), IFilter.MAX_FREQUENCY);
                defaultParamsElement.setAttribute (DelugeTag.HPF_FREQUENCY, convertToHex (normalizedFrequency));
                defaultParamsElement.setAttribute (DelugeTag.HPF_RESONANCE, convertToHex (filter.getResonance ()));
            }
        }

        // Add envelope settings
        if (!groups.isEmpty () && !groups.get (0).getSampleZones ().isEmpty ())
        {
            final ISampleZone zone = groups.get (0).getSampleZones ().get (0);
            final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();

            final Element envelope1Element = XMLUtils.addElement (document, defaultParamsElement, DelugeTag.ENVELOPE1);
            envelope1Element.setAttribute (DelugeTag.ATTACK, convertEnvelopeTimeToHex (amplitudeEnvelope.getAttackTime ()));
            envelope1Element.setAttribute (DelugeTag.DECAY, convertEnvelopeTimeToHex (amplitudeEnvelope.getDecayTime ()));
            envelope1Element.setAttribute (DelugeTag.SUSTAIN, convertToHex (amplitudeEnvelope.getSustainLevel ()));
            envelope1Element.setAttribute (DelugeTag.RELEASE, convertEnvelopeTimeToHex (amplitudeEnvelope.getReleaseTime ()));
        }

        // Add a second envelope with defaults
        final Element envelope2Element = XMLUtils.addElement (document, defaultParamsElement, DelugeTag.ENVELOPE2);
        envelope2Element.setAttribute (DelugeTag.ATTACK, "0xE6666654");
        envelope2Element.setAttribute (DelugeTag.DECAY, "0xE6666654");
        envelope2Element.setAttribute (DelugeTag.SUSTAIN, "0xFFFFFFE9");
        envelope2Element.setAttribute (DelugeTag.RELEASE, "0xE6666654");

        // Add velocity to volume patch cable
        final Element patchCablesElement = XMLUtils.addElement (document, defaultParamsElement, DelugeTag.PATCH_CABLES);
        final Element patchCableElement = XMLUtils.addElement (document, patchCablesElement, DelugeTag.PATCH_CABLE);
        patchCableElement.setAttribute (DelugeTag.SOURCE, "velocity");
        patchCableElement.setAttribute (DelugeTag.DESTINATION, "volume");
        patchCableElement.setAttribute (DelugeTag.AMOUNT, "0x3FFFFFE8");

        // Add default equalizer
        final Element equalizerElement = XMLUtils.addElement (document, defaultParamsElement, DelugeTag.EQUALIZER);
        equalizerElement.setAttribute (DelugeTag.BASS, "0x00000000");
        equalizerElement.setAttribute (DelugeTag.TREBLE, "0x00000000");
        equalizerElement.setAttribute (DelugeTag.BASS_FREQUENCY, "0x00000000");
        equalizerElement.setAttribute (DelugeTag.TREBLE_FREQUENCY, "0x00000000");

        // Add default arpeggiator
        final Element arpeggiatorElement = XMLUtils.addElement (document, rootElement, DelugeTag.ARPEGGIATOR);
        arpeggiatorElement.setAttribute (DelugeTag.MODE_ATTR, "off");
        arpeggiatorElement.setAttribute (DelugeTag.NUM_OCTAVES, "2");
        arpeggiatorElement.setAttribute (DelugeTag.SYNC_LEVEL, "7");
        arpeggiatorElement.setAttribute (DelugeTag.SYNC_TYPE, "0");

        // Add default mod knobs
        createDefaultModKnobs (document, rootElement);

        // Add default delay
        final Element delayElement = XMLUtils.addElement (document, rootElement, DelugeTag.DELAY);
        delayElement.setAttribute (DelugeTag.PING_PONG, "1");
        delayElement.setAttribute (DelugeTag.ANALOG, "0");
        delayElement.setAttribute (DelugeTag.SYNC_LEVEL, "7");
        delayElement.setAttribute (DelugeTag.SYNC_TYPE, "0");

        // Add default sidechain
        final Element sidechainElement = XMLUtils.addElement (document, rootElement, DelugeTag.SIDECHAIN);
        sidechainElement.setAttribute (DelugeTag.ATTACK, "327244");
        sidechainElement.setAttribute (DelugeTag.RELEASE, "936");
        sidechainElement.setAttribute (DelugeTag.SYNC_LEVEL, "6");
        sidechainElement.setAttribute (DelugeTag.SYNC_TYPE, "0");

        // Add default audio compressor
        final Element compressorElement = XMLUtils.addElement (document, rootElement, DelugeTag.AUDIO_COMPRESSOR);
        compressorElement.setAttribute (DelugeTag.ATTACK, "83886080");
        compressorElement.setAttribute (DelugeTag.RELEASE, "83886080");
        compressorElement.setAttribute (DelugeTag.THRESH, "0");
        compressorElement.setAttribute (DelugeTag.RATIO, "1073741824");
        compressorElement.setAttribute (DelugeTag.COMP_HPF, "0");
        compressorElement.setAttribute (DelugeTag.COMP_BLEND, "2147483647");

        return this.createXMLString (document);
    }


    /**
     * Set the default parameters on the defaultParams element.
     *
     * @param defaultParamsElement The defaultParams element
     */
    private static void setDefaultParams (final Element defaultParamsElement)
    {
        defaultParamsElement.setAttribute (DelugeTag.ARPEGGIATOR_GATE, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.PORTAMENTO, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.COMPRESSOR_SHAPE, "0xDC28F5B2");
        defaultParamsElement.setAttribute (DelugeTag.OSC_A_VOLUME, "0x7FFFFFFF");
        defaultParamsElement.setAttribute (DelugeTag.OSC_A_PULSE_WIDTH, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.OSC_A_WAVETABLE_POSITION, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.OSC_B_VOLUME, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.OSC_B_PULSE_WIDTH, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.OSC_B_WAVETABLE_POSITION, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.NOISE_VOLUME, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.VOLUME, "0x4CCCCCA8");
        defaultParamsElement.setAttribute (DelugeTag.PAN, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.LPF_FREQUENCY, "0x7FFFFFFF");
        defaultParamsElement.setAttribute (DelugeTag.LPF_RESONANCE, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.HPF_FREQUENCY, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.HPF_RESONANCE, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.LFO1_RATE, "0x1999997E");
        defaultParamsElement.setAttribute (DelugeTag.LFO2_RATE, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.MODULATOR1_AMOUNT, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.MODULATOR1_FEEDBACK, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.MODULATOR2_AMOUNT, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.MODULATOR2_FEEDBACK, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.CARRIER1_FEEDBACK, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.CARRIER2_FEEDBACK, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.MOD_FX_RATE, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.MOD_FX_DEPTH, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.DELAY_RATE, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.DELAY_FEEDBACK, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.REVERB_AMOUNT, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.ARPEGGIATOR_RATE, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.STUTTER_RATE, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.SAMPLE_RATE_REDUCTION, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.BIT_CRUSH, "0x80000000");
        defaultParamsElement.setAttribute (DelugeTag.MOD_FX_OFFSET, "0x00000000");
        defaultParamsElement.setAttribute (DelugeTag.MOD_FX_FEEDBACK, "0x00000000");
    }


    /**
     * Create the default mod knobs element.
     *
     * @param document The XML document
     * @param rootElement The root element
     */
    private static void createDefaultModKnobs (final Document document, final Element rootElement)
    {
        final Element modKnobsElement = XMLUtils.addElement (document, rootElement, DelugeTag.MOD_KNOBS);
        addModKnob (document, modKnobsElement, "pan", null);
        addModKnob (document, modKnobsElement, "volumePostFX", null);
        addModKnob (document, modKnobsElement, "lpfResonance", null);
        addModKnob (document, modKnobsElement, "lpfFrequency", null);
        addModKnob (document, modKnobsElement, "env1Release", null);
        addModKnob (document, modKnobsElement, "env1Attack", null);
        addModKnob (document, modKnobsElement, "delayFeedback", null);
        addModKnob (document, modKnobsElement, "delayRate", null);
        addModKnob (document, modKnobsElement, "reverbAmount", null);
        addModKnob (document, modKnobsElement, "volumePostReverbSend", "compressor");
        addModKnob (document, modKnobsElement, "pitch", "lfo1");
        addModKnob (document, modKnobsElement, "lfo1Rate", null);
        addModKnob (document, modKnobsElement, "portamento", null);
        addModKnob (document, modKnobsElement, "stutterRate", null);
        addModKnob (document, modKnobsElement, "bitcrushAmount", null);
        addModKnob (document, modKnobsElement, "sampleRateReduction", null);
    }


    /**
     * Add a mod knob element.
     *
     * @param document The XML document
     * @param modKnobsElement The parent modKnobs element
     * @param controlsParam The parameter this knob controls
     * @param patchAmountFromSource Optional source for patch amount, may be null
     */
    private static void addModKnob (final Document document, final Element modKnobsElement, final String controlsParam, final String patchAmountFromSource)
    {
        final Element modKnobElement = XMLUtils.addElement (document, modKnobsElement, DelugeTag.MOD_KNOB);
        modKnobElement.setAttribute (DelugeTag.CONTROLS_PARAM, controlsParam);
        if (patchAmountFromSource != null)
            modKnobElement.setAttribute (DelugeTag.PATCH_AMOUNT_FROM_SOURCE, patchAmountFromSource);
    }


    /**
     * Resolve root keys for zones where the root key is not set (-1). First attempts to read the
     * root note from the sample data (e.g. WAV SMPL chunk). Falls back to the midpoint of the
     * zone's key range.
     *
     * @param groups The groups containing the sample zones
     */
    private static void resolveRootKeys (final List<IGroup> groups)
    {
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (zone.getKeyRoot () >= 0)
                    continue;
                try
                {
                    if (zone.getSampleData () != null)
                        zone.getSampleData ().addZoneData (zone, true, false);
                }
                catch (final IOException ex)
                {
                    // Ignore, fall through to key range fallback
                }
                if (zone.getKeyRoot () < 0)
                    zone.setKeyRoot ((zone.getKeyLow () + zone.getKeyHigh ()) / 2);
            }
    }


    /**
     * Flatten all zones from all groups into a single layer with unique key ranges. The Deluge
     * requires each rangeTopNote to be unique. When multiple zones cover the same key (e.g.
     * velocity layers), the zone whose velocity range is closest to velocity 100 is selected since
     * it best represents a normal playing dynamic.
     *
     * @param groups The groups containing the sample zones
     * @return A sorted list of zones with non-overlapping, unique key ranges
     */
    private static List<ISampleZone> flattenToSingleLayer (final List<IGroup> groups)
    {
        // Collect all zones and pick the best one per root key
        final TreeMap<Integer, ISampleZone> bestByRoot = new TreeMap<> ();
        for (final IGroup group: groups)
        {
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final int root = zone.getKeyRoot ();
                final ISampleZone existing = bestByRoot.get (Integer.valueOf (root));
                if (existing == null || velocityScore (zone) < velocityScore (existing))
                    bestByRoot.put (Integer.valueOf (root), zone);
            }
        }

        // Sort selected zones by root key
        final List<ISampleZone> sorted = new ArrayList<> (bestByRoot.values ());
        sorted.sort (Comparator.comparingInt (ISampleZone::getKeyRoot));

        // Assign non-overlapping key ranges so each zone gets a unique rangeTopNote
        for (int i = 0; i < sorted.size (); i++)
        {
            final ISampleZone zone = sorted.get (i);

            // Key low: 0 for first zone, otherwise previous zone's high + 1
            final int keyLow = i == 0 ? 0 : sorted.get (i - 1).getKeyHigh () + 1;
            zone.setKeyLow (keyLow);

            // Key high: for the last zone extend to 127, otherwise midpoint to next root
            if (i < sorted.size () - 1)
            {
                final int nextRoot = sorted.get (i + 1).getKeyRoot ();
                final int midpoint = (zone.getKeyRoot () + nextRoot) / 2;
                zone.setKeyHigh (Math.max (midpoint, keyLow));
            }
            else
                zone.setKeyHigh (127);
        }

        return sorted;
    }


    /**
     * Score a zone by how far its velocity midpoint is from 100 (normal playing dynamic). Lower
     * score is better.
     *
     * @param zone The zone to score
     * @return The distance from the ideal velocity midpoint
     */
    private static int velocityScore (final ISampleZone zone)
    {
        final int mid = (zone.getVelocityLow () + zone.getVelocityHigh ()) / 2;
        return Math.abs (mid - 100);
    }


    /**
     * Creates the metadata for one sample range.
     *
     * @param document The XML document
     * @param sampleRangesElement The sampleRanges element where to add the sample range
     * @param zone Where to get the sample info from
     * @param trim Trim to start/end if true
     * @param samplesRelativePath The relative path from SD card root to the sample folder
     */
    private static void createSampleRange (final Document document, final Element sampleRangesElement, final ISampleZone zone, final boolean trim, final String samplesRelativePath)
    {
        // Create the sampleRange element
        final Element sampleRangeElement = XMLUtils.addElement (document, sampleRangesElement, DelugeTag.SAMPLE_RANGE);

        // Set the key range (omit rangeTopNote for the last zone matching Deluge convention)
        final int keyHigh = zone.getKeyHigh ();
        if (keyHigh < 127)
            sampleRangeElement.setAttribute (DelugeTag.RANGE_TOP_NOTE, Integer.toString (keyHigh));

        // Set the filename as path relative to SD card root
        final String filename = samplesRelativePath + "/" + createSafeFilename (zone.getName ()) + ".wav";
        sampleRangeElement.setAttribute (DelugeTag.FILE_NAME, filename);

        // Set the root note as transpose from middle C (clamped to -127..127)
        final int transpose = Math.clamp (60 - zone.getKeyRoot (), -127, 127);
        sampleRangeElement.setAttribute (DelugeTag.TRANSPOSE, Integer.toString (transpose));

        // Set fine tuning (clamped to Deluge range of -50..50 cents)
        final double tune = zone.getTuning ();
        final int cents = Math.clamp ((int) (tune * 100), -50, 50);
        if (cents != 0)
            sampleRangeElement.setAttribute (DelugeTag.CENTS, Integer.toString (cents));

        // Always create a zone element with startSamplePos and endSamplePos as the Deluge requires
        final int start = zone.getStart ();
        final int stop = zone.getStop ();
        final List<ISampleLoop> loops = zone.getLoops ();

        final Element zoneElement = XMLUtils.addElement (document, sampleRangeElement, DelugeTag.ZONE);

        // When trimmed, the audio data starts at 0 in the output file
        final int effectiveStart = trim ? 0 : start;
        zoneElement.setAttribute (DelugeTag.START_SAMPLE_POS, Integer.toString (effectiveStart));

        // endSamplePos: use the zone stop if set, otherwise use sample data length
        if (stop > 0)
        {
            final int effectiveEnd = trim ? stop - start : stop;
            zoneElement.setAttribute (DelugeTag.END_SAMPLE_POS, Integer.toString (effectiveEnd));
        }
        else
        {
            // Get actual sample length from the sample data
            try
            {
                final int sampleLength = zone.getSampleData ().getAudioMetadata ().getNumberOfSamples ();
                if (sampleLength > 0)
                    zoneElement.setAttribute (DelugeTag.END_SAMPLE_POS, Integer.toString (sampleLength));
            }
            catch (final IOException ex)
            {
                // If we can't read the sample length, omit endSamplePos
            }
        }

        // Add loop points if present
        final boolean hasLoop = !loops.isEmpty () && loops.get (0).getEnd () > loops.get (0).getStart ();
        if (hasLoop)
        {
            final ISampleLoop loop = loops.get (0);
            int loopStart = trim ? loop.getStart () - start : loop.getStart ();
            int loopEnd = trim ? loop.getEnd () - start : loop.getEnd ();

            // Validate: loop start must be >= 1, loop end must be <= endSamplePos
            loopStart = Math.max (1, loopStart);
            final String endStr = zoneElement.getAttribute (DelugeTag.END_SAMPLE_POS);
            if (endStr != null && !endStr.isEmpty ())
            {
                final int endPos = Integer.parseInt (endStr);
                loopEnd = Math.min (loopEnd, endPos);
            }

            if (loopEnd > loopStart)
            {
                zoneElement.setAttribute (DelugeTag.START_LOOP_POS, Integer.toString (loopStart));
                zoneElement.setAttribute (DelugeTag.END_LOOP_POS, Integer.toString (loopEnd));
            }
        }
    }


    /**
     * Convert an envelope time in seconds to a Deluge hex string.
     *
     * @param timeInSeconds The time in seconds
     * @return The hex string
     */
    private static String convertEnvelopeTimeToHex (final double timeInSeconds)
    {
        final double normalized = Math.clamp (timeInSeconds / MAX_ENVELOPE_TIME, 0.0, 1.0);
        return convertToHex (normalized);
    }


    /**
     * Convert a normalized value (0-1) to a hex string in the format used by Deluge. The Deluge
     * uses signed 32-bit hex values where 0x80000000 represents 0.0 and 0x7FFFFFFF represents 1.0.
     *
     * @param value The normalized value (0-1)
     * @return The hex string
     */
    private static String convertToHex (final double value)
    {
        final double clamped = Math.clamp (value, 0.0, 1.0);
        final int intValue = (int) Math.round ((clamped * 2 - 1) * 0x7FFFFFFF);
        return String.format ("0x%08X", intValue);
    }
}