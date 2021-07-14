package orioni.sixdice;

import orioni.jz.awt.image.ImageUtilities;
import orioni.jz.awt.image.RestrictableIndexColorModel;
import orioni.jz.common.exception.ParseException;
import orioni.jz.io.FileType;
import orioni.jz.io.PrimitiveInputStream;
import orioni.jz.io.RandomAccessByteArrayInputStream;
import orioni.jz.util.Pair;
import orioni.jz.util.ProgressTracker;
import orioni.jz.util.Utilities;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

// TODO: add support for the BAM codec to analyze frames to determine if they are identical
// The BAM file format supports a frame index table which allows multiple directions to share the same frame, as
// long as that frame is identical in every way.  This should be optional, however, as comparing all of the frames
// would be a fairly expensive N^2 operation and the user may not wish to spend that kind of time compressing the
// file.

/**
 * This {@link AnimationCodec} is intended to support reading and writing BAM files.  The BAM format is used in games
 * such as Baldur's Gate, Planescape: Torment, and the Icewind Dale series.
 *
 * @author Zachary Palmer
 */
public class BAMCodec extends AnimationCodec
{
// STATIC FIELDS /////////////////////////////////////////////////////////////////

// CONSTANTS /////////////////////////////////////////////////////////////////////

// NON-STATIC FIELDS /////////////////////////////////////////////////////////////

// CONSTRUCTORS //////////////////////////////////////////////////////////////////

    /**
     * General constructor.
     */
    public BAMCodec()
    {
        super();
    }

// NON-STATIC METHODS ////////////////////////////////////////////////////////////

    /**
     * Checks the provided {@link Animation} object to ensure that it can be written in the format supported by this
     * codec.
     *
     * @param animation The {@link Animation} to examine.
     * @return A {@link java.util.List} of {@link orioni.jz.util.Pair}<code>&lt;{@link String},{@link
     *         orioni.sixdice.AnimationCodec.MessageType}&gt;</code> objects noting information about the {@link
     *         orioni.sixdice.Animation}.
     */
    public List<Pair<String, MessageType>> check(Animation animation)
    {
        List<Pair<String, MessageType>> ret = new ArrayList<Pair<String, MessageType>>();
        if (animation.getDirectionCount() > 255)
        {
            ret.add(
                    new Pair<String, MessageType>(
                            "BAM files cannot contain more than 255 directions.",
                            MessageType.FATAL));
        }
        return ret;
    }

    /**
     * Specifies that BAM files <i>do</i> contain their own palettes.
     *
     * @return <code>true</code> if the animation format contains its own palette; <code>false</code> otherwise.
     */
    public boolean formatContainsPalette()
    {
        return true;
    }

    /**
     * Retrieves a {@link FileType} object describing the types of files which can be used by this codec. This
     * recommendation is provided to assist programs in recognizing files which will be valid to pass to {@link
     * AnimationCodec#read(File,RestrictableIndexColorModel)}.
     *
     * @return The {@link orioni.jz.io.FileType} of files of the type used by this codec.
     */
    public FileType getFileType()
    {
        return new FileType("BAM Files (*.bam)");
    }

    /**
     * Retrieves a human-readable name which describes this {@link AnimationCodec}.  This is used for nothing more than
     * display purposes.
     *
     * @return The human-readable name of this codec.
     */
    public String getName()
    {
        return "BAM Codec";
    }

    /**
     * Decodes an {@link Animation} from the specified data array.  Most often, this will be a <code>byte[]</code> of
     * the entire file's contents.  The intention of this method is to contain all of the file I/O handling in the
     * {@link AnimationCodec} class.
     *
     * @param data    The <code>byte[]</code> containing the data to decode.
     * @param palette Ignored.  BAM files contain their own palettes.
     * @param tracker The {@link ProgressTracker} that tracks this decoding operation.
     * @return The decoded {@link Animation}.
     * @throws ParseException       If the provided data cannot be decoded by this codec.
     * @throws NullPointerException If <code>palette</code> is <code>null</code> and this codec reads files which do not
     *                              carry their own palettes.
     */
    public Animation decode(byte[] data, RestrictableIndexColorModel palette, ProgressTracker tracker)
            throws ParseException
    {
        // TODO: manage ProgressTracker
        RandomAccessByteArrayInputStream rabais = new RandomAccessByteArrayInputStream(data);
        PrimitiveInputStream pis = new PrimitiveInputStream(rabais, PrimitiveInputStream.LITTLE_ENDIAN);
        byte[] buf = new byte[4];
        try
        {
            pis.readFully(buf);
            String signature = new String(buf, "US-ASCII");
            pis.readFully(buf);
            String version = new String(buf, "US-ASCII");
            if ("BAMC".equals(signature))
            {
                if ("V1  ".equals(version))
                {
                    // next four bytes are information for programs that need info about uncompressed size
                    if (data.length <= 12) throw new ParseException("Compressed BAM had no data.");
                    byte[] zcomp = new byte[data.length - 12];
                    System.arraycopy(data, 12, zcomp, 0, zcomp.length);
                    try
                    {
                        return decode(Utilities.inflate(zcomp), palette, tracker);
                    } catch (DataFormatException e)
                    {
                        throw new ParseException("Could not read compressed BAM: compressed data corrupted.");
                    }
                } else
                {
                    throw new ParseException(
                            "Unrecognized compressed BAM version \"" + version +
                            "\" (this codec only accepts version 1).");
                }
            } else if ("BAM ".equals(signature))
            {
                // ***********************
                // * DECODE THE BAM FILE *
                // ***********************

                ArrayList<String> warnings = new ArrayList<String>();

                // *** READ HEADER ***
                int totalFrames = pis.readUnsignedShort();
                int directions = pis.readUnsignedByte();
                byte transparentIndex = pis.readByte();

                int frameEntriesOffset = pis.readInt();
                int paletteOffset = pis.readInt();
                int frameLookupTableOffset = pis.readInt();

                // *** READ FRAME ENTRIES AND "CYCLE" (DIRECTION) ENTRIES ***
                rabais.seek(frameEntriesOffset);
                int[] frameWidths = new int[totalFrames];
                int[] frameHeights = new int[totalFrames];
                int[] frameXOffsets = new int[totalFrames];
                int[] frameYOffsets = new int[totalFrames];
                int[] frameDataOffsets = new int[totalFrames];
                boolean[] frameCompressed = new boolean[totalFrames];
                for (int i = 0; i < totalFrames; i++)
                {
                    frameWidths[i] = pis.readUnsignedShort();
                    frameHeights[i] = pis.readUnsignedShort();
                    // BAM files store the offset of the center of the frame... we'll have to adjust it.
                    frameXOffsets[i] = pis.readShort() - (frameWidths[i] / 2);
                    frameYOffsets[i] = pis.readShort() - (frameHeights[i] / 2);
                    int offsetData = pis.readInt();
                    frameDataOffsets[i] = offsetData & 0x7FFFFFFF;
                    frameCompressed[i] = ((offsetData & 0x80000000) != 0);
                }

                int[] directionFrameCount = new int[directions];
                int[] directionFrameTableStartingIndex = new int[directions];
                int frameLookupTableSize = 0;
                int maxFramesInDirection = 0;
                for (int i = 0; i < directions; i++)
                {
                    directionFrameCount[i] = pis.readUnsignedShort();
                    directionFrameTableStartingIndex[i] = pis.readUnsignedShort();
                    maxFramesInDirection = Math.max(maxFramesInDirection, directionFrameCount[i]);
                    frameLookupTableSize = Math.max(
                            frameLookupTableSize,
                            directionFrameCount[i] + directionFrameTableStartingIndex[i]);
                }

                // *** READ PALETTE ***
                rabais.seek(paletteOffset);
                int[] paletteData = new int[256];
                // this isn't too hard... BAM files store palette data in the format blue, green, red, 0x00...
                // and we're reading in Little-Endian format.  ;)
                // There is no certain information on how the fourth byte is used.
                for (int i = 0; i < paletteData.length; i++) paletteData[i] = pis.readInt() | 0xFF000000;
                paletteData[transparentIndex & 0xFF] = 0x00000000; // simple transparency

                // *** READ FRAME LOOKUP TABLE ***
                rabais.seek(frameLookupTableOffset);
                int[] frameLookupTable = new int[frameLookupTableSize];
                for (int i = 0; i < frameLookupTable.length; i++) frameLookupTable[i] = pis.readUnsignedShort();

                // *** READ FRAME DATA ***
                BufferedImage[] frameImage = new BufferedImage[totalFrames];
                for (int frame = 0; frame < totalFrames; frame++)
                {
                    BufferedImage image = new BufferedImage(
                            frameWidths[frame], frameHeights[frame], BufferedImage.TYPE_INT_ARGB);
                    frameImage[frame] = image;
                    rabais.seek(frameDataOffsets[frame]);

                    byte[] frameData = new byte[frameWidths[frame] * frameHeights[frame]];
                    if (frameCompressed[frame])
                    {
                        // read compressed frame data
                        int index = 0;
                        while (index < frameData.length)
                        {
                            byte signal = pis.readByte();
                            if (signal == transparentIndex)
                            {
                                int count = pis.readUnsignedByte() + 1;
                                if (index + count > frameData.length)
                                {
                                    count = frameData.length - index;
                                    warnings.add(
                                            "Raw Frame " + frame +
                                            ": Decompressed RLE frame data exceeded frame length.  Truncating.");
                                }
                                for (int i = 0; i < count; i++)
                                {
                                    frameData[index + i] = transparentIndex;
                                }
                                index += count;
                            } else
                            {
                                frameData[index++] = signal;
                            }
                        }
                    } else
                    {
                        // read uncompressed frame data
                        pis.readFully(frameData);
                    }

                    int imageWidth = image.getWidth();
                    for (int i = 0; i < frameData.length; i++)
                    {
                        image.setRGB(i % imageWidth, i / imageWidth, paletteData[frameData[i] & 0xFF]);
                    }
                }

                // *** BUILD ANIMATION ***
                Animation ret = new Animation(null, directions, maxFramesInDirection, warnings);
                for (int d = 0; d < directions; d++)
                {
                    int startIndex = directionFrameTableStartingIndex[d];
                    for (int fidx = 0; fidx < directionFrameCount[d]; fidx++)
                    {
                        int frameIndex = frameLookupTable[startIndex + fidx];
                        ret.setFrame(
                                d, fidx, new AnimationFrame(
                                ImageUtilities.copyImage(frameImage[frameIndex]),
                                frameXOffsets[frameIndex], frameYOffsets[frameIndex]));
                    }
                }
                return ret;
            } else
            {
                throw new ParseException("Unrecognized signature \"" + signature + "\" (probably not a BAM file).");
            }
        } catch (IOException ioe)
        {
            // This can't happen unless ByteArrayInputStream throws an IOException
            throw new IllegalStateException("ByteArrayInputStream threw an IOException!", ioe);
        }
    }

    /**
     * Encodes an {@link Animation}, storing it in a <code>byte[]</code>.  Most often, this byte array will then be
     * immediately written to a file as its entire contents.  The intention of this method is to contain all of the file
     * I/O handling in the {@link AnimationCodec} class.
     *
     * @param animation The {@link Animation} to encode.
     * @param palette   The palette in which to encode the animation.
     * @param tracker   The {@link ProgressTracker} that tracks this decoding operation.
     * @return The encoded data.
     * @throws NullPointerException If <code>palette</code> is <code>null</code> and this codec writes files which do
     *                              not carry their own palettes.
     */
    public byte[] encode(Animation animation, RestrictableIndexColorModel palette, ProgressTracker tracker)
    {
        return new byte[0];  // TODO: Consider proper implementation of this method.
    }

// STATIC METHODS ////////////////////////////////////////////////////////////////

}

// END OF FILE