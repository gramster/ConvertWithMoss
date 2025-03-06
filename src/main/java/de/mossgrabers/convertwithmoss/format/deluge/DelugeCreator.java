// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.deluge;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

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

        // Create the SYNTHS and SAMPLES folders
        final File synthsFolder = new File (destinationFolder, "SYNTHS");
        safeCreateDirectory (synthsFolder);

        final File samplesFolder = new File (destinationFolder, "SAMPLES");
        safeCreateDirectory (samplesFolder);

        // Create a safe filename for the multisample
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Create a subfolder for this instrument's samples
        final File instrumentSamplesFolder = new File (samplesFolder, sampleName);
        safeCreateDirectory (instrumentSamplesFolder);

        // Create the XML file
        final Optional<String> metadata = this.createMetadata (sampleName, multisampleSource, trim);
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
     * @return The XML structure
     */
    private Optional<String> createMetadata (final String instrumentName, final IMultisampleSource multisampleSource, final boolean trim)
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

        // Create the osc1 element which will contain the samples
        final Element osc1Element = XMLUtils.addElement (document, rootElement, DelugeTag.OSC1);
        osc1Element.setAttribute (DelugeTag.TYPE, "sample");
        osc1Element.setAttribute (DelugeTag.TRANSPOSE, "0");
        osc1Element.setAttribute (DelugeTag.CENTS, "0");
        osc1Element.setAttribute (DelugeTag.LINEAR_INTERPOLATION, "1");
        osc1Element.setAttribute (DelugeTag.LOOP_MODE, "1");

        // Create the sampleRanges element
        final Element sampleRangesElement = XMLUtils.addElement (document, osc1Element, DelugeTag.SAMPLE_RANGES);

        // Add all sample zones
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        for (final IGroup group: groups)
        {
            for (final ISampleZone zone: group.getSampleZones ())
            {
                createSampleRange (document, sampleRangesElement, zone, trim, instrumentName);
            }
        }

        // Create default parameters
        final Element defaultParamsElement = XMLUtils.addElement (document, rootElement, DelugeTag.DEFAULT_PARAMS);

        // Set volume and pan
        defaultParamsElement.setAttribute (DelugeTag.VOLUME, "0x4CCCCCA8");
        defaultParamsElement.setAttribute (DelugeTag.PAN, "0x00000000");

        // Set filter parameters
        final Optional<IFilter> optFilter = multisampleSource.getGlobalFilter ();
        if (optFilter.isPresent ())
        {
            final IFilter filter = optFilter.get ();
            final FilterType type = filter.getType ();

            if (type == FilterType.LOW_PASS)
            {
                final double normalizedFrequency = MathUtils.normalizeFrequency (filter.getCutoff (), IFilter.MAX_FREQUENCY);
                final String hexFrequency = convertToHex (normalizedFrequency);
                defaultParamsElement.setAttribute (DelugeTag.LPF_FREQUENCY, hexFrequency);

                final String hexResonance = convertToHex (filter.getResonance ());
                defaultParamsElement.setAttribute (DelugeTag.LPF_RESONANCE, hexResonance);
            }
            else if (type == FilterType.HIGH_PASS)
            {
                final double normalizedFrequency = MathUtils.normalizeFrequency (filter.getCutoff (), IFilter.MAX_FREQUENCY);
                final String hexFrequency = convertToHex (normalizedFrequency);
                defaultParamsElement.setAttribute (DelugeTag.HPF_FREQUENCY, hexFrequency);

                final String hexResonance = convertToHex (filter.getResonance ());
                defaultParamsElement.setAttribute (DelugeTag.HPF_RESONANCE, hexResonance);
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

        return this.createXMLString (document);
    }


    /**
     * Creates the metadata for one sample range.
     *
     * @param document The XML document
     * @param sampleRangesElement The sampleRanges element where to add the sample range
     * @param zone Where to get the sample info from
     * @param trim Trim to start/end if true
     * @param instrumentName The name of the instrument (used for subfolder path)
     */
    private static void createSampleRange (final Document document, final Element sampleRangesElement, final ISampleZone zone, final boolean trim, final String instrumentName)
    {
        // Create the sampleRange element
        final Element sampleRangeElement = XMLUtils.addElement (document, sampleRangesElement, DelugeTag.SAMPLE_RANGE);

        // Set the filename as relative path from SYNTHS to SAMPLES folder
        final String filename = "../SAMPLES/" + instrumentName + "/" + zone.getName () + ".wav";
        XMLUtils.addTextElement (document, sampleRangeElement, DelugeTag.FILE_NAME, filename);

        // Set the key range
        XMLUtils.addTextElement (document, sampleRangeElement, DelugeTag.RANGE_TOP_NOTE, Integer.toString (zone.getKeyHigh ()));

        // Set the root note as transpose from middle C
        XMLUtils.addTextElement (document, sampleRangeElement, DelugeTag.TRANSPOSE, Integer.toString (60 - zone.getKeyRoot ()));

        // Set fine tuning
        final double tune = zone.getTuning ();
        if (tune != 0)
        {
            final int cents = (int) (tune * 100);
            XMLUtils.addTextElement (document, sampleRangeElement, DelugeTag.CENTS, Integer.toString (cents));
        }

        // Set reversed flag
        if (zone.isReversed ())
            XMLUtils.addTextElement (document, sampleRangeElement, DelugeTag.REVERSED, "1");

        // Determine if we need a zone element
        final int start = zone.getStart ();
        final int stop = zone.getStop ();
        final List<ISampleLoop> loops = zone.getLoops ();
        final boolean hasStartEnd = (!trim && start > 0) || stop > 0;
        final boolean hasLoop = !loops.isEmpty () && loops.get (0).getEnd () > loops.get (0).getStart ();

        if (hasStartEnd || hasLoop)
        {
            final Element zoneElement = XMLUtils.addElement (document, sampleRangeElement, DelugeTag.ZONE);

            // When trimmed, the audio data starts at 0 in the output file
            if (!trim && start > 0)
                XMLUtils.addTextElement (document, zoneElement, DelugeTag.START_SAMPLE_POS, Integer.toString (start));

            if (stop > 0)
            {
                final int end = trim ? stop - start : stop;
                XMLUtils.addTextElement (document, zoneElement, DelugeTag.END_SAMPLE_POS, Integer.toString (end));
            }

            if (hasLoop)
            {
                final ISampleLoop loop = loops.get (0);
                final int loopStart = trim ? loop.getStart () - start : loop.getStart ();
                final int loopEnd = trim ? loop.getEnd () - start : loop.getEnd ();

                XMLUtils.addTextElement (document, zoneElement, DelugeTag.START_LOOP_POS, Integer.toString (loopStart));
                XMLUtils.addTextElement (document, zoneElement, DelugeTag.END_LOOP_POS, Integer.toString (loopEnd));
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