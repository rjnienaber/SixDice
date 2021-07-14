package orioni.sixdice;

import orioni.jz.awt.image.RestrictableIndexColorModel;
import orioni.jz.common.exception.ParseException;
import orioni.jz.io.FileType;
import orioni.jz.io.PrimitiveInputStream;
import orioni.jz.io.PrimitiveOutputStream;
import orioni.jz.io.RandomAccessByteArrayInputStream;
import orioni.jz.util.Pair;
import orioni.jz.util.ProgressTracker;
import orioni.jz.util.strings.StringUtilities;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This {@link AnimationCodec} is designed to read and write DC6 files.
 *
 * @author Zachary Palmer
 */
public class DC6Codec extends AnimationCodec
{
// STATIC FIELDS /////////////////////////////////////////////////////////////////

// CONSTANTS /////////////////////////////////////////////////////////////////////

// NON-STATIC FIELDS /////////////////////////////////////////////////////////////

// CONSTRUCTORS //////////////////////////////////////////////////////////////////

    /**
     * General constructor.
     */
    public DC6Codec()
    {
        super();
    }

// NON-STATIC METHODS ////////////////////////////////////////////////////////////

    /**
     * Specifies that DC6 files do not contain their own palettes.
     *
     * @return <code>false</code>, always.
     */
    public boolean formatContainsPalette()
    {
        return false;
    }

    /**
     * Retrieves the name of this codec: "DC6 Codec."
     *
     * @return The name of this codec.
     */
    public String getName()
    {
        return "DC6 Codec";
    }

    /**
     * Checks the provided {@link Animation} object to ensure that it can be written in the format supported by this
     * codec.
     *
     * @param animation The {@link Animation} to examine.
     * @return A {@link List} of {@link Pair}<code>&lt;{@link String},{@link orioni.sixdice.AnimationCodec.MessageType}&gt;</code>
     *         objects noting information about the {@link Animation}.
     */
    public List<Pair<String, MessageType>> check(Animation animation)
    {
        List<Pair<String, MessageType>> ret = new ArrayList<Pair<String, MessageType>>();
        for (int direction = 0; direction < animation.getDirectionCount(); direction++)
        {
            for (int frameIndex = 0; frameIndex < animation.getFrameCount(); frameIndex++)
            {
                AnimationFrame frame = animation.getFrame(direction, frameIndex);
                if ((frame.getImage().getHeight() > 256) || (frame.getImage().getWidth() > 256))
                {
                    ret.add(
                            new Pair<String, MessageType>(
                                    "Direction " + direction + ", Frame " + frameIndex + ": " +
                                    "Diablo II does not accept DC6 files with frames containing images larger than 256x256.",
                                    MessageType.WARNING));
                }
                if (frame.getOptionalData().length > 0)
                {
                    ret.add(
                            new Pair<String, MessageType>(
                                    "Direction " + direction + ", Frame " + frameIndex + ": " +
                                    "DC6 files cannot contain optional frame data.  This data will be overwritten.",
                                    MessageType.WARNING));
                }
            }
        }
        return ret;
    }

    /**
     * Retrieves a {@link FileType} object describing the types of files which can be used by this codec.
     *
     * @return The {@link FileType} of files of the type used by this codec.
     */
    public FileType getFileType()
    {
        return new FileType("DC6 Files (*.dc6)", "dc6");
    }

    /**
     * Reads an {@link Animation} from the specified {@link File}.
     *
     * @param encodedData The <code>byte[]</code> containing the encoded_data to decode.
     * @param palette     The {@link RestrictableIndexColorModel} to use when reading the {@link Animation} object.
     * @param tracker     The {@link ProgressTracker} which is used to track this method's progress.
     * @return The {@link Animation} which was read from the {@link File}.
     * @throws ParseException If the provided {@link File} cannot be read by this codec.
     */
    public Animation decode(byte[] encodedData, RestrictableIndexColorModel palette, ProgressTracker tracker)
            throws ParseException
    {
        palette = deriveCodecPalette(palette);

        List<String> warnings = new ArrayList<String>();
        List<AnimationFrame> frameList = new ArrayList<AnimationFrame>();

        RandomAccessByteArrayInputStream rabais = new RandomAccessByteArrayInputStream(encodedData);

        try
        {
            byte mostTransparent = (byte) (palette.getMostTransparentIndex());

            // read header
            PrimitiveInputStream pis = new PrimitiveInputStream(rabais, PrimitiveInputStream.LITTLE_ENDIAN);
            ParseException.performParseAssertion(pis.readInt() == 6, "The input file was not a DC6 file.");
            ParseException.performParseAssertion(pis.readInt() == 1, "The input file was not a DC6 file.");
            ParseException.performParseAssertion(pis.readInt() == 0, "The input file was not a DC6 file.");
            int magicNumber = pis.readInt();
            if (!((magicNumber == 0xEEEEEEEE) || (magicNumber == 0xCDCDCDCD)))
            {
                warnings.add(
                        "DC6 header's \"magic number\" terminator was 0x" + StringUtilities.padLeft(
                                Long.toString(magicNumber & 0xFFFFFFFFL, 16), '0', 8));
            }
            int directions = pis.readInt();
            int frames = pis.readInt();

            // read offset table
            int[] offsetTable = new int[frames * directions];
            for (int i = 0; i < offsetTable.length; i++)
            {
                offsetTable[i] = pis.readInt();
            }

            for (int i = 0; i < frames * directions; i++)
            {
                frameList.add(new AnimationFrame());
            }

            tracker.setStartingValue(0);
            tracker.setEndingValue(offsetTable.length);

            // read frames
            for (int i = 0; i < offsetTable.length; i++)
            {
                rabais.seek(offsetTable[i]);
                int flip = pis.readInt();
                int width = pis.readInt();
                int height = pis.readInt();
                int offsetX = pis.readInt();
                int offsetY = pis.readInt();
                pis.readInt();  // unused
                int nextBlock = pis.readInt();
                if ((i < offsetTable.length - 1) && (nextBlock != offsetTable[i + 1]))
                {
                    warnings.add(
                            "Frame #" +
                            i +
                            ": \"next_block\" entry does not properly indicate the next block.  " +
                            "Using offset table.");
                }
                int length = pis.readInt();

                byte[] codedData = new byte[length];
                //noinspection ResultOfMethodCallIgnored
                pis.read(codedData);

                // decoding time
                String bufferedNewlineWarning = null;
                byte[] decodedData = new byte[width * height];
                // initialize decoded encoded_data to "transparent"
                for (int j = 0; j < decodedData.length; j++) decodedData[j] = mostTransparent;
                // begin decoding
                int xPosition = 0;
                int yPosition = (flip == 0) ? height - 1 : 0;
                int yIncrement = flip * 2 - 1;  // 1 if flip, -1 if not flip
                ByteArrayInputStream bais = new ByteArrayInputStream(codedData);
                int data = bais.read();
                while (data != -1)
                {
                    if (data == 0x80)
                    {
                        // newline signal
                        yPosition += yIncrement;
                        xPosition = 0;
                        // buffer the newline warning in case no new encoded_data is written after the newline
                        if (yPosition < 0)
                        {
                            bufferedNewlineWarning = "Frame #" + i +
                                                     ": more newlines than rows.  Cursor reset to top line.";
                        } else if (yPosition >= height)
                        {
                            bufferedNewlineWarning = "Frame #" + i +
                                                     ": more newlines than rows.  Cursor reset to bottom line.";
                        }
                    } else
                    {
                        if (bufferedNewlineWarning != null)
                        {
                            warnings.add(bufferedNewlineWarning);
                            bufferedNewlineWarning = null;
                        }
                        int sequenceLength = data & 0x7F;
                        byte[] sequence = new byte[sequenceLength];
                        // initialize sequence to transparent
                        for (int j = 0; j < sequence.length; j++) sequence[j] = mostTransparent;
                        // check command byte
                        if ((data & 0x80) == 0)
                        {
                            // raw encoded_data sequence
                            int amountRead = bais.read(sequence);
                            if (amountRead < sequenceLength)
                            {
                                warnings.add(
                                        "Frame #" + i +
                                        ": specified sequence length is greater than length of file.  Assuming " +
                                        "transparent for missing samples.");
                            }
                        }
                        if (sequenceLength > width - xPosition)
                        {
                            warnings.add(
                                    "Frame #" + i +
                                    ": specified sequence would exceed width of image.  Truncating.");
                            sequenceLength = width - xPosition;
                        }
                        System.arraycopy(sequence, 0, decodedData, xPosition + yPosition * width, sequenceLength);
                        xPosition += sequenceLength;
                    }

                    data = bais.read();
                }

                // Okay, that should be the decoded image.
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, palette);
                WritableRaster r = image.getRaster();
                // Ugh... create an array that the Rasters can use...
                int[] intDecodedData = new int[decodedData.length];
                int index = 0;
                for (byte b : decodedData) intDecodedData[index++] = b;
                r.setPixels(r.getMinX(), r.getMinY(), width, height, intDecodedData);

                // Yuck.  Okay, that should be a usable Image object.  Now add the frame to our list.
                frameList.set(i, new AnimationFrame(image, offsetX, offsetY));

                tracker.incrementProgress(1);
            }

            Animation ret = new Animation(frameList, directions, frames);
            ret.addWarnings(warnings);
            return ret;
        } catch (EOFException eofe)
        {
            throw new ParseException("Unexpected end of file.", eofe);
        } catch (IOException ioe)
        {
            throw new ParseException(ioe.getMessage(), ioe);
        }
    }

    /**
     * Encodes an {@link Animation} in DC6 format.  This method should not be called unless a call to {@link
     * AnimationCodec#check(Animation)} using the same {@link Animation} object produces no messages with a {@link
     * orioni.sixdice.AnimationCodec.MessageType#FATAL} type.
     *
     * @param animation The {@link Animation} to write.
     * @param palette   The palette in which to write the {@link Animation}.
     * @param tracker   The {@link ProgressTracker} which tracks the progress of this method.
     * @return The encoded data.
     */
    public byte[] encode(Animation animation, RestrictableIndexColorModel palette, ProgressTracker tracker)
    {
        palette = deriveCodecPalette(palette);

        ByteArrayOutputStream encodingBuffer = new ByteArrayOutputStream();

        try
        {
            // the starting index for frame data
            int fileIndex = 24 + 4 * animation.getDirectionCount() * animation.getFrameCount();
            final int frameHeaderSize = 32;
            int[] offsetTable = new int[animation.getDirectionCount() * animation.getFrameCount()];
            byte[][] encodedFrames = new byte[animation.getDirectionCount() * animation.getFrameCount()][];
            tracker.setStartingValue(0);
            tracker.setEndingValue(offsetTable.length);

            for (int direction = 0; direction < animation.getDirectionCount(); direction++)
            {
                for (int frame = 0; frame < animation.getFrameCount(); frame++)
                {
                    offsetTable[direction * animation.getFrameCount() + frame] = fileIndex;

                    // We're going to have to do this in steps, since writing the header requires knowledge of the size
                    // of the encoded data segments.
                    BufferedImage image = animation.getFrame(direction, frame).getImage();
                    // First, let's actually encode the data.
                    byte[] encodedDataSegment = encodeFrame(animation, direction, frame, palette);

                    // now write the header
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrimitiveOutputStream pos = new PrimitiveOutputStream(baos, PrimitiveOutputStream.LITTLE_ENDIAN);
                    pos.writeInt(0); // no flip
                    pos.writeInt(image.getWidth(null)); // width
                    pos.writeInt(image.getHeight(null)); // height
                    pos.writeInt(animation.getFrame(direction, frame).getXOffset()); // offset_x
                    pos.writeInt(animation.getFrame(direction, frame).getYOffset()); // offset_y
                    pos.writeInt(0); // unused
                    fileIndex += frameHeaderSize + encodedDataSegment.length + 3; // for the footer below
                    pos.writeInt(fileIndex);
                    pos.writeInt(encodedDataSegment.length);
                    // and attach the body
                    pos.write(encodedDataSegment);
                    // and that's the frame block
                    pos.close();
                    baos.close();
                    encodedFrames[frame + direction * animation.getFrameCount()] = baos.toByteArray();
                    tracker.incrementProgress(1);
                }
            }

            // write a DC6 header
            PrimitiveOutputStream realPos = new PrimitiveOutputStream(
                    encodingBuffer, PrimitiveOutputStream.LITTLE_ENDIAN);
            realPos.writeInt(6); // DC version number
            realPos.writeInt(1);
            realPos.writeInt(0);

            realPos.writeInt(0xCDCDCDCD); // magic number
            realPos.writeInt(animation.getDirectionCount());
            realPos.writeInt(animation.getFrameCount());

            // write the offset table
            for (int offset : offsetTable) realPos.writeInt(offset);

            // write the frame blocks
            for (byte[] frameBlock : encodedFrames)
            {
                realPos.write(frameBlock);
                // terminate each frame with a bunch of CDs... goodness knows why
                realPos.write(0xCD);
                realPos.write(0xCD);
                realPos.write(0xCD);
            }

            // Looks like we're finished. ::::) <-- happy spider
            encodingBuffer.close();
        } catch (IOException ioe)
        {
            // This can't happen unless ByteArrayOutputStream throws an IOException
            throw new IllegalStateException("ByteArrayOutputStream threw an IOException!", ioe);
        }
        return encodingBuffer.toByteArray();
    }

// STATIC METHODS ////////////////////////////////////////////////////////////////

    /**
     * This method encodes the specified frame from the provided {@link Animation} using DC6 ecoding.  This method is
     * abstracted from the {@link DC6Codec#encode(Animation, RestrictableIndexColorModel, ProgressTracker)} method
     * primarily because the {@link DCCCodec#encode(Animation, RestrictableIndexColorModel, ProgressTracker)} method
     * needs to use it to determine the amount of memory that prebuffering programs will need to store the decoded DCC
     * data.
     *
     * @param animation The {@link Animation} with the frame to encode.
     * @param direction The direction index of the frame to encode.
     * @param frame     The frame index of the frame to encode.
     * @return The frame as a DC6-encoded byte array.
     */
    public static byte[] encodeFrame(Animation animation, int direction, int frame,
                                     RestrictableIndexColorModel palette)
    {
        try
        {
            BufferedImage image = animation.getFrame(direction, frame).getImage();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int yOffset = image.getHeight() - 1;
            int xOffset = 0;

            int width = image.getWidth();
            int height = image.getHeight();
            int[] rgbs = image.getRGB(0, 0, width, height, null, 0, width);
            int[] rawdata = new int[width * height];
            for (int i = 0; i < rgbs.length; i++)
            {
                rawdata[i] = palette.find(rgbs[i]);
            }

            int mostTransparent = palette.getMostTransparentIndex();
            int transparentPixelsBuffer;
            final int imageWidth = image.getWidth(); // cached to reduce method calls
            while (yOffset >= 0)
            {
                transparentPixelsBuffer = 0;
                final int imageWidthTimesYOffset = imageWidth * yOffset; // cached to avoid arithmetic
                while (xOffset < imageWidth)
                {
                    // If a transparency signal was detected before this data, write it now
                    if (transparentPixelsBuffer > 0)
                    {
                        while (transparentPixelsBuffer > 127)
                        {
                            baos.write(0xFF);
                            transparentPixelsBuffer -= 127;
                        }
                        if (transparentPixelsBuffer > 0)
                        {
                            baos.write(0x80 | transparentPixelsBuffer);
                            transparentPixelsBuffer = 0;
                        }
                    }
                    if (rawdata[imageWidthTimesYOffset + xOffset] == mostTransparent)
                    {
                        // Write a series of transparent data.  To do that, first we'll have to find out how much
                        // transparent data there is.
                        while ((xOffset < imageWidth) &&
                               (rawdata[imageWidthTimesYOffset + xOffset] == mostTransparent))
                        {
                            xOffset++;
                            transparentPixelsBuffer++;
                        }
                        // we've determined the number of transparent pixels...
                        // The signal is now buffered so we don't write it out if a newline is coming.
                    } else
                    {
                        // Write a raw data indicator followed by the raw data.  We'll have to determine how much
                        // raw data there is, but we can buffer it as we go.
                        byte[] realRaw = new byte[127];
                        int count = 0;
                        while ((xOffset < imageWidth) &&
                               (!(rawdata[imageWidthTimesYOffset + xOffset] == mostTransparent)) &&
                               (count < 127))
                        {
                            realRaw[count] = (byte) (rawdata[imageWidthTimesYOffset + xOffset]);
                            xOffset++;
                            count++;
                        }
                        // we've determined the number of raw pixels
                        assert(count != 0x80);
                        baos.write(count);
                        baos.write(realRaw, 0, count);
                    }
                }
                baos.write(0x80); // DC6 newline... wow, this is a screwed up format
                yOffset--;
                xOffset = 0;
            }
            baos.close();
            return baos.toByteArray();
        } catch (IOException ioe)
        {
            // This can only happen if ByteArrayOutputStream throws an IOException... which it doesn't.
            throw new IllegalStateException("ByteArrayOutputStream threw an IOException!", ioe);
        }
    }
}

// END OF FILE