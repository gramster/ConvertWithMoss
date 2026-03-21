// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.deluge;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively Synthstrom Deluge multi-sample files in folders. Files must have the .XML
 * extension and contain a sound element with sample oscillator type.
 *
 * @author Jürgen Moßgraber
 */
public class DelugeDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String ENDING_XML        = ".xml";
    private static final String SAMPLES_FOLDER    = "SAMPLES";

    /** Maximum envelope time in seconds used for normalizing Deluge hex values. */
    private static final double MAX_ENVELOPE_TIME = 20.0;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DelugeDetector (final INotifier notifier)
    {
        super ("Deluge", "Deluge", notifier, new MetadataSettingsUI ("Deluge"), ENDING_XML);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final FileInputStream in = new FileInputStream (file))
        {
            final String content = StreamUtils.readUTF8 (in).trim ();
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (file, file.getParent (), document);
        }
        catch (final IOException | SAXException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The XML file
     * @param basePath The parent folder
     * @param document The XML document to parse
     * @return The parsed multi-sample source
     */
    private List<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final String basePath, final Document document)
    {
        final Element top = document.getDocumentElement ();
        if (!DelugeTag.SOUND.equals (top.getNodeName ()))
            return Collections.emptyList ();

        // Find the osc1 element which contains the sample information
        final Element osc1Element = XMLUtils.getChildElementByName (top, DelugeTag.OSC1);
        if (osc1Element == null)
            return Collections.emptyList ();

        // Only process sample-type oscillators
        final String oscType = osc1Element.getAttribute (DelugeTag.TYPE);
        if (oscType != null && !oscType.isBlank () && !"sample".equals (oscType))
            return Collections.emptyList ();

        // Get the sample ranges
        final Element sampleRangesElement = XMLUtils.getChildElementByName (osc1Element, DelugeTag.SAMPLE_RANGES);
        if (sampleRangesElement == null)
            return Collections.emptyList ();

        final List<Element> sampleRangeElements = XMLUtils.getChildElementsByName (sampleRangesElement, DelugeTag.SAMPLE_RANGE);
        if (sampleRangeElements.isEmpty ())
            return Collections.emptyList ();

        // Create the multisample source
        final File parentFile = multiSampleFile.getParentFile ();
        final String name = FileUtils.getNameWithoutType (multiSampleFile);
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, name);

        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));
        final IGroup group = new DefaultGroup ("Group");
        multisampleSource.setGroups (Collections.singletonList (group));

        // Process each sample range
        for (final Element sampleRangeElement: sampleRangeElements)
        {
            final Optional<ISampleZone> optZone = this.createSampleZone (multisampleSource, sampleRangeElement, basePath);
            if (optZone.isPresent ())
                group.addSampleZone (optZone.get ());
        }

        if (group.getSampleZones ().isEmpty ())
            return Collections.emptyList ();

        // Process envelope settings
        processEnvelopeSettings (top, group);

        // Process filter settings
        processFilterSettings (top, multisampleSource);

        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (multisampleSource.getGroups ()), parts);
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Create a sample zone from a sample range element.
     *
     * @param multisampleSource The multisample source
     * @param sampleRangeElement The sample range element
     * @param basePath The base path for resolving sample file paths
     * @return The sample zone, if successful
     */
    private Optional<ISampleZone> createSampleZone (final IMultisampleSource multisampleSource, final Element sampleRangeElement, final String basePath)
    {
        // Try attribute first, then child element (older Deluge firmware uses child elements)
        String fileName = sampleRangeElement.getAttribute (DelugeTag.FILE_NAME);
        if (fileName == null || fileName.isBlank ())
        {
            final Element fileNameElement = XMLUtils.getChildElementByName (sampleRangeElement, DelugeTag.FILE_NAME);
            if (fileNameElement != null)
                fileName = fileNameElement.getTextContent ();
        }
        if (fileName == null || fileName.isBlank ())
            return Optional.empty ();

        // Resolve the sample file path
        final File sampleFile = resolveSampleFile (fileName, basePath);
        if (sampleFile == null)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", fileName);
            return Optional.empty ();
        }

        final String zoneName = FileUtils.getNameWithoutType (sampleFile);
        final ISampleData sampleData;
        try
        {
            if (!AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                return Optional.empty ();
            sampleData = new WavFileSampleData (sampleFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_FILE", ex);
            return Optional.empty ();
        }

        final ISampleZone sampleZone = new DefaultSampleZone (zoneName, sampleData);

        // Read key range, transpose, and cents (try attributes first, then child elements)
        final int rangeTopNote = readIntParam (sampleRangeElement, DelugeTag.RANGE_TOP_NOTE, 127);
        final int transpose = readIntParam (sampleRangeElement, DelugeTag.TRANSPOSE, 0);
        final int cents = readIntParam (sampleRangeElement, DelugeTag.CENTS, 0);

        // Set root note (transpose is offset from middle C)
        sampleZone.setKeyRoot (60 - transpose);
        sampleZone.setTuning (cents / 100.0);

        // Calculate key low from the previous zone's top note + 1
        int keyLow = 0;
        final List<ISampleZone> existingZones = multisampleSource.getGroups ().get (0).getSampleZones ();
        if (!existingZones.isEmpty ())
            keyLow = existingZones.get (existingZones.size () - 1).getKeyHigh () + 1;

        sampleZone.setKeyLow (keyLow);
        sampleZone.setKeyHigh (rangeTopNote);
        sampleZone.setVelocityLow (1);
        sampleZone.setVelocityHigh (127);

        // Process zone settings (sample start/end and loop points)
        final Element zoneElement = XMLUtils.getChildElementByName (sampleRangeElement, DelugeTag.ZONE);
        if (zoneElement != null)
        {
            final int startSamplePos = readIntParam (zoneElement, DelugeTag.START_SAMPLE_POS, 0);
            final int endSamplePos = readIntParam (zoneElement, DelugeTag.END_SAMPLE_POS, -1);
            final int startLoopPos = readIntParam (zoneElement, DelugeTag.START_LOOP_POS, -1);
            final int endLoopPos = readIntParam (zoneElement, DelugeTag.END_LOOP_POS, -1);

            sampleZone.setStart (startSamplePos);
            if (endSamplePos > 0)
                sampleZone.setStop (endSamplePos);

            // Add loop if loop points are defined
            if (startLoopPos >= 0 && endLoopPos > startLoopPos)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (LoopType.FORWARDS);
                loop.setStart (startLoopPos);
                loop.setEnd (endLoopPos);
                sampleZone.addLoop (loop);
            }
        }

        // Check if the sample is reversed
        final boolean reversed = XMLUtils.getIntegerAttribute (sampleRangeElement, DelugeTag.REVERSED, 0) == 1;
        sampleZone.setReversed (reversed);

        return Optional.of (sampleZone);
    }


    /**
     * Resolve a sample file path from a Deluge fileName value.
     *
     * @param fileName The fileName from the XML (may be relative)
     * @param basePath The base path (typically the folder containing the XML file)
     * @return The resolved file, or null if not found
     */
    private static File resolveSampleFile (final String fileName, final String basePath)
    {
        // Try relative to the XML file location
        File sampleFile = new File (basePath, fileName);
        if (sampleFile.exists ())
            return sampleFile;

        // Try in a SAMPLES folder at the same level as the SYNTHS folder
        final File parentFolder = new File (basePath).getParentFile ();
        if (parentFolder != null)
        {
            final File samplesFolder = new File (parentFolder, SAMPLES_FOLDER);
            if (samplesFolder.exists ())
            {
                sampleFile = new File (samplesFolder, fileName);
                if (sampleFile.exists ())
                    return sampleFile;
            }
        }

        return null;
    }


    /**
     * Read an integer parameter from an element, trying the attribute first and then falling back to
     * a child element (older Deluge firmware uses child elements instead of attributes).
     *
     * @param element The parent element
     * @param tagName The attribute/element name
     * @param defaultValue The default value if not found
     * @return The integer value
     */
    private static int readIntParam (final Element element, final String tagName, final int defaultValue)
    {
        // Try attribute first
        final int attrValue = XMLUtils.getIntegerAttribute (element, tagName, Integer.MIN_VALUE);
        if (attrValue != Integer.MIN_VALUE)
            return attrValue;

        // Try child element
        final Element childElement = XMLUtils.getChildElementByName (element, tagName);
        if (childElement != null)
        {
            try
            {
                return Integer.parseInt (childElement.getTextContent ().trim ());
            }
            catch (final NumberFormatException ex)
            {
                // Fall through to default
            }
        }

        return defaultValue;
    }


    /**
     * Process envelope settings from the XML file.
     *
     * @param rootElement The root element
     * @param group The group to apply the envelope settings to
     */
    private static void processEnvelopeSettings (final Element rootElement, final IGroup group)
    {
        final Element defaultParamsElement = XMLUtils.getChildElementByName (rootElement, DelugeTag.DEFAULT_PARAMS);
        if (defaultParamsElement == null)
            return;

        final Element envelope1Element = XMLUtils.getChildElementByName (defaultParamsElement, DelugeTag.ENVELOPE1);
        if (envelope1Element == null)
            return;

        for (final ISampleZone zone: group.getSampleZones ())
        {
            final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();

            final String attackStr = envelope1Element.getAttribute (DelugeTag.ATTACK);
            final String decayStr = envelope1Element.getAttribute (DelugeTag.DECAY);
            final String sustainStr = envelope1Element.getAttribute (DelugeTag.SUSTAIN);
            final String releaseStr = envelope1Element.getAttribute (DelugeTag.RELEASE);

            if (attackStr != null && !attackStr.isBlank ())
                amplitudeEnvelope.setAttackTime (convertHexToEnvelopeTime (attackStr));

            if (decayStr != null && !decayStr.isBlank ())
                amplitudeEnvelope.setDecayTime (convertHexToEnvelopeTime (decayStr));

            if (sustainStr != null && !sustainStr.isBlank ())
                amplitudeEnvelope.setSustainLevel (convertHexToNormalizedValue (sustainStr));

            if (releaseStr != null && !releaseStr.isBlank ())
                amplitudeEnvelope.setReleaseTime (convertHexToEnvelopeTime (releaseStr));
        }
    }


    /**
     * Process filter settings from the XML file.
     *
     * @param rootElement The root element
     * @param multisampleSource The multisample source to apply the filter settings to
     */
    private static void processFilterSettings (final Element rootElement, final IMultisampleSource multisampleSource)
    {
        final Element defaultParamsElement = XMLUtils.getChildElementByName (rootElement, DelugeTag.DEFAULT_PARAMS);
        if (defaultParamsElement == null)
            return;

        final String lpfFrequencyStr = defaultParamsElement.getAttribute (DelugeTag.LPF_FREQUENCY);
        final String lpfResonanceStr = defaultParamsElement.getAttribute (DelugeTag.LPF_RESONANCE);
        final String hpfFrequencyStr = defaultParamsElement.getAttribute (DelugeTag.HPF_FREQUENCY);
        final String hpfResonanceStr = defaultParamsElement.getAttribute (DelugeTag.HPF_RESONANCE);

        if ((lpfFrequencyStr == null || lpfFrequencyStr.isBlank ()) &&
            (hpfFrequencyStr == null || hpfFrequencyStr.isBlank ()))
            return;

        final String filterRoute = rootElement.getAttribute (DelugeTag.FILTER_ROUTE);
        final boolean useLowPass = !"H2L".equals (filterRoute);

        if (useLowPass && lpfFrequencyStr != null && !lpfFrequencyStr.isBlank ())
        {
            final double cutoff = convertHexToNormalizedValue (lpfFrequencyStr);
            final double resonance = lpfResonanceStr != null && !lpfResonanceStr.isBlank ()
                ? convertHexToNormalizedValue (lpfResonanceStr)
                : 0;

            multisampleSource.setGlobalFilter (new DefaultFilter (FilterType.LOW_PASS, 4,
                MathUtils.denormalizeFrequency (cutoff, IFilter.MAX_FREQUENCY),
                resonance));
        }
        else if (!useLowPass && hpfFrequencyStr != null && !hpfFrequencyStr.isBlank ())
        {
            final double cutoff = convertHexToNormalizedValue (hpfFrequencyStr);
            final double resonance = hpfResonanceStr != null && !hpfResonanceStr.isBlank ()
                ? convertHexToNormalizedValue (hpfResonanceStr)
                : 0;

            multisampleSource.setGlobalFilter (new DefaultFilter (FilterType.HIGH_PASS, 4,
                MathUtils.denormalizeFrequency (cutoff, IFilter.MAX_FREQUENCY),
                resonance));
        }
    }


    /**
     * Convert a Deluge hex envelope time value to seconds.
     *
     * @param hexValue The hex string value
     * @return The time in seconds
     */
    static double convertHexToEnvelopeTime (final String hexValue)
    {
        return convertHexToNormalizedValue (hexValue) * MAX_ENVELOPE_TIME;
    }


    /**
     * Convert a hex string value (like "0x7FFFFFFF") to a normalized double value between 0 and 1.
     * The Deluge uses signed 32-bit hex values where 0x80000000 maps to 0.0 and 0x7FFFFFFF maps to
     * 1.0.
     *
     * @param hexValue The hex string value
     * @return The normalized double value
     */
    static double convertHexToNormalizedValue (final String hexValue)
    {
        if (hexValue == null || hexValue.isBlank ())
            return 0.0;

        try
        {
            long value = Long.parseLong (hexValue.replace ("0x", "").replace ("0X", ""), 16);

            // Convert to signed 32-bit integer if necessary
            if ((value & 0x80000000L) != 0)
                value = value - 0x100000000L;

            // Normalize to 0-1 range: -2^31 -> 0.0, 2^31-1 -> 1.0
            return (value + 2147483648.0) / 4294967295.0;
        }
        catch (final NumberFormatException ex)
        {
            return 0.0;
        }
    }
}