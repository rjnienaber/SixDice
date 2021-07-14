package orioni.sixdice;

import orioni.jz.awt.color.TransparencyCriticizingSampleDifferenceComparator;
import orioni.jz.awt.image.ImageUtilities;
import orioni.jz.awt.image.RestrictableIndexColorModel;
import orioni.jz.common.exception.ParseException;
import orioni.jz.io.*;
import orioni.jz.io.bit.*;
import orioni.jz.math.MathUtilities;
import orioni.jz.util.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * This {@link AnimationCodec} implementation reads and writes DCC files.
 *
 * @author Zachary Palmer
 */
public class DCCCodec extends AnimationCodec
{
// STATIC FIELDS /////////////////////////////////////////////////////////////////

// CONSTANTS /////////////////////////////////////////////////////////////////////

    /**
     * The DCC file format uses a compressed bit size field to describe the size of data in a frame's header.  This
     * array is a representation of that compression function: the index of the array is the value <code>n</code> while
     * the number at that index is <code>f(n)</code>.
     */
    private static final int[] DCC_SIZE_COMPRESSION_FUNCTION = new int[]{0, 1, 2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 26,
            28, 30, 32};
    /**
     * This array represents the inverse of the function represented by {@link DCC_SIZE_COMPRESSION_FUNCTION}. The value
     * <code>-1</code> indicates that there is no <code>f<sup>-1</sup>(n)</code> for the provided <code>n</code>.
     */
    private static final int[] DCC_SIZE_COMPRESSION_FUNCTION_INVERTED =
            new int[]{0, 1, 2, -1, 3, -1, 4, -1, 5, -1, 6, -1, 7, -1, 8, -1, 9, -1, -1, -1, 10, -1, -1, -1, 11, -1, 12,
                    -1, 13, -1, 14, -1, 15};

    /**
     * A singleton instance of the {@link DCCCodec}.
     */
    public static final DCCCodec SINGLETON = new DCCCodec();

// NON-STATIC FIELDS /////////////////////////////////////////////////////////////

// CONSTRUCTORS //////////////////////////////////////////////////////////////////

    /**
     * General constructor.
     */
    public DCCCodec()
    {
        super();
    }

// NON-STATIC METHODS ////////////////////////////////////////////////////////////

    /**
     * Specifies that DCC files do not contain their own palettes.
     *
     * @return <code>false</code>, always.
     */
    public boolean formatContainsPalette()
    {
        return false;
    }

    /**
     * Retrieves the name of this codec: "DCC Codec".
     *
     * @return The name of this codec.
     */
    public String getName()
    {
        return "DCC Codec";
    }

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
        ArrayList<Pair<String, MessageType>> ret = new ArrayList<Pair<String, MessageType>>();
        if (animation.getDirectionCount() > 32)
        {
            ret.add(
                    new Pair<String, MessageType>(
                            "Diablo II is reportedly incapable of reading DCCs with more than 32 directions.",
                            MessageType.WARNING));
        }
        if (animation.getFrameCount() > 256)
        {
            ret.add(
                    new Pair<String, MessageType>(
                            "Diablo II is reportedly incapable of reading DCCs with more than 256 frames per " +
                            "direction.",
                            MessageType.WARNING));
        }
        if (animation.getDirectionCount() > 255)
        {
            ret.add(
                    new Pair<String, MessageType>(
                            "DCC files cannot store more than 255 directions.",
                            MessageType.FATAL));
        }
        return ret;
    }

    /**
     * Retrieves a {@link orioni.jz.io.FileType} object describing the types of files which can be used by this codec.
     *
     * @return The {@link orioni.jz.io.FileType} of files of the type used by this codec.
     */
    public FileType getFileType()
    {
        return new FileType("DCC Files (*.dcc)", "dcc");
    }

    /**
     * Reads an {@link Animation} from the specified {@link java.io.File}.
     *
     * @param data    The <code>byte[]</code> containing the encoded DCC.
     * @param palette The {@link RestrictableIndexColorModel} in which to render the animation.
     * @param tracker The {@link ProgressTracker} which tracks the progress of this operation.
     * @return The {@link Animation} which was read from the {@link java.io.File}.
     * @throws orioni.jz.common.exception.ParseException
     *          If the provided {@link java.io.File} cannot be read by this codec.
     */
    public Animation decode(byte[] data, RestrictableIndexColorModel palette, ProgressTracker tracker)
            throws ParseException
    {
        palette = deriveCodecPalette(palette);

        RandomAccessByteArrayInputStream rabais = new RandomAccessByteArrayInputStream(data);

        try
        {
            PrimitiveInputStream pis = new PrimitiveInputStream(rabais, PrimitiveInputStream.LITTLE_ENDIAN);
            java.util.List<String> warnings = new ArrayList<String>();
            ParseException.performParseAssertion(pis.read() == 0x74, "This is not a DCC file.");
            int version = pis.read();
            if (version != 0x06)
            {
                warnings.add("DCC file has a version which is not 6.  The reader may not properly read this file.");
            }
            int directions = pis.read();
            if (directions > 32)
            {
                warnings.add(
                        "DCC file has more than 32 directions.  Diablo II should fail an assertion with this file.");
            }
            int frames = pis.readInt();
            if (frames > 256)
            {
                warnings.add(
                        "DCC file has more than 256 frames per direction.  Diablo II should fail an assertion " +
                        "with this file.");
            }
            ParseException.performParseAssertion(
                    frames >= 0,
                    "This DCC file has more than two billion frames per direction.  Probably not a DCC file.");
            if (pis.readInt() != 1) warnings.add("DCC file's magic number is not 0x01.");

            // DEBUG: The following statement describes the TotalSizeCoded of the DCC being read.
            /*int tsc = */pis.readInt(); // skip the information about necessary buffer size - we don't need that
            //System.err.println("Reading DCC: TotalSizeCoded is " + tsc);

            int[] directionOffsetTable = new int[directions];
            for (int i = 0; i < directionOffsetTable.length; i++)
            {
                directionOffsetTable[i] = pis.readInt();
            }

            tracker.setStartingValue(0);
            tracker.setEndingValue(directions);
            // That's the DCC main header.  Now read each direction.  From each direction, obtain a list of
            // AnimationFrames.
            java.util.List<AnimationFrame> animationFrames = new ArrayList<AnimationFrame>();
            for (int i = 0; i < directionOffsetTable.length; i++)
            {
                rabais.seek(directionOffsetTable[i]);
                animationFrames.addAll(readDCCDirection(rabais, i, frames, palette, warnings));
                tracker.incrementProgress(1);
            }

            tracker.setProgressCompleted();
            return new Animation(animationFrames, directions, frames);
        } catch (IOException ioe)
        {
            // This can't happen unless ByteArrayInputStream throws an IOException
            throw new IllegalStateException("ByteArrayInputStream threw an IOException!", ioe);
        }
    }

    /**
     * Reads a single DCC direction from a {@link orioni.jz.io.bit.BitInputStream}.
     *
     * @param source    The {@link orioni.jz.io.bit.BitInputStream} from which to read.
     * @param direction The direction number.  Purely for the construction of warning strings.
     * @param frames    The number of frames which appear in this direction.
     * @param model     The {@link orioni.jz.awt.image.RestrictableIndexColorModel} for this direction.
     * @param warnings  A list of warnings to which any warnings generated by this operation should be added.
     * @return A list of {@link AnimationFrame}s, one for each direction which was read.
     * @throws IOException    If an I/O error occurs while reading the direction.
     * @throws ParseException If the provided {@link orioni.jz.io.bit.BitInputStream} does not appear to contain a DCC
     *                        direction.
     */
    private static List<AnimationFrame> readDCCDirection(InputStream source, int direction, int frames,
                                                         RestrictableIndexColorModel model, List<String> warnings)
            throws IOException, ParseException
    {
        try
        {
            byte transparentIndex = (byte) (model.getMostTransparentIndex());

            // construct return buffer as an array just as an extra precaution that we don't return the wrong number of
            // frames
            AnimationFrame[] ret = new AnimationFrame[frames];

            BitInputStream bis = new BitInputStream(source, BitOrder.LOWEST_BIT_FIRST, EndianFormat.LITTLE_ENDIAN);

            // for reading the actual bytes
            PrimitiveInputStream pis = new PrimitiveInputStream(bis, PrimitiveInputStream.LITTLE_ENDIAN);

            // read the frame headers
            pis.readInt(); // read the 32-bit buffer size description; we don't need that
            boolean compressionFlagB = bis.readBoolean(); // encodingtypebitstream & rawpixelcodesbitstream present
            boolean compressionFlagA = bis.readBoolean(); // equalcellsbitstream present
            int var0Bits = DCC_SIZE_COMPRESSION_FUNCTION[bis.readBits(4)];
            int widthBits = DCC_SIZE_COMPRESSION_FUNCTION[bis.readBits(4)];
            int heightBits = DCC_SIZE_COMPRESSION_FUNCTION[bis.readBits(4)];
            int xOffsetBits = DCC_SIZE_COMPRESSION_FUNCTION[bis.readBits(4)];
            int yOffsetBits = DCC_SIZE_COMPRESSION_FUNCTION[bis.readBits(4)];
            int optionalDataBits = DCC_SIZE_COMPRESSION_FUNCTION[bis.readBits(4)];
            int codedBytesBits = DCC_SIZE_COMPRESSION_FUNCTION[bis.readBits(4)];

            DCCFrameHeader[] frameHeaders = new DCCFrameHeader[frames];
            boolean doAlign = false;
            for (int frame = 0; frame < frames; frame++)
            {
                if (bis.readBits(var0Bits) != 0)
                {
                    warnings.add("Direction " + direction + " Frame " + frame + ": Var0 did not contain a zero.");
                }
                frameHeaders[frame] = new DCCFrameHeader(
                        bis.readBits(widthBits),
                        bis.readBits(heightBits),
                        bis.readBitsSigned(xOffsetBits),
                        bis.readBitsSigned(yOffsetBits),
                        bis.readBits(optionalDataBits),
                        bis.readBits(codedBytesBits),
                        bis.readBoolean());

                if (frameHeaders[frame].getOptionalDataSize() > 0) doAlign = true;

                // Ensure Y-offset is the *upper* left corner... in top-down frames, it's the lower left corner.
                if (!frameHeaders[frame].isBottomUp())
                {
                    // the stored offset includes the bottom pixel row, hence the 1
                    frameHeaders[frame].setYOffset(
                            frameHeaders[frame].getYOffset() - frameHeaders[frame].getHeight() + 1);
                }
            }

            // Found frame headers.  Read optional frame data.
            if (doAlign)
            {
                bis.findByteBoundary();
                for (DCCFrameHeader frameHeader : frameHeaders)
                {
                    if (frameHeader.getOptionalDataSize() > 0)
                    {
                        byte[] buffer = new byte[frameHeader.getOptionalDataSize()];
                        int len = buffer.length;
                        int off = 0;
                        while (len > 0)
                        {
                            int read = bis.read(buffer, off, len);
                            if (read == -1)
                            {
                                throw new EOFException("Unexpected end of stream while reading optional frame data.");
                            } else
                            {
                                off += read;
                                len -= read;
                            }
                        }
                        frameHeader.setOptionalData(buffer);
                    }
                }
            }

            // DEBUG: This set of statements is designed to provide information obtained by the DCC reader.
//            System.err.println("DCC direction "+direction+" header info:");
//            System.err.println("    EqualCellsBitstream:   "+compression_flag_a);
//            System.err.println("    EncodingTypeBitstream: "+compression_flag_b);
//            System.err.println("    Var0 Bits:             "+var0_bits);
//            System.err.println("    Width Bits:            "+width_bits);
//            System.err.println("    Height Bits:           "+height_bits);
//            System.err.println("    X Offset Bits:         "+x_offset_bits);
//            System.err.println("    Y Offset Bits:         "+y_offset_bits);
//            System.err.println("    Optional Data Bits:    "+optional_data_bits);
//            System.err.println("    Coded Bytes Bits:      "+coded_bytes_bits);
//            for (int f=0;f<frames;f++)
//            {
//                System.err.println("    Frame "+f+":");
//                System.err.println("        Width:              "+frame_headers[f].getWidth());
//                System.err.println("        Height:             "+frame_headers[f].getHeight());
//                System.err.println("        X Offset:           "+frame_headers[f].getXOffset());
//                System.err.println("        Y Offset:           "+frame_headers[f].getYOffset());
//                System.err.println("        Optional Data Size: "+frame_headers[f].getOptionalDataSize());
//                System.err.println("        Coded Bytes:        "+frame_headers[f].getCodedBytes());
//            }

            // Obtain bitstreams
            int equalCellsBitstreamSize = -1;
            int pixelMaskBitstreamSize;
            int encodingTypeBistreamSize = -1;
            int rawPixelCodesBitstreamSize = -1;
            BitInputStream equalCellsBitstream;
            BitInputStream equalCellsBitstream2;
            BitInputStream encodingTypeBitstream;
            BitInputStream rawPixelCodesBitstream;
            BitInputStream pixelMaskBitstream;

            if (compressionFlagA)
            {
                equalCellsBitstreamSize = bis.readBits(20);
            }
            pixelMaskBitstreamSize = bis.readBits(20);
            if (compressionFlagB)
            {
                encodingTypeBistreamSize = bis.readBits(20);
                rawPixelCodesBitstreamSize = bis.readBits(20);
            }

            byte[] pixelValuesMapping = new byte[256];
            int pixelValuesKeyIndex = 0;
            for (int i = 0; i < 256; i++)
            {
                if (bis.readBoolean())
                {
                    pixelValuesMapping[pixelValuesKeyIndex++] = (byte) i;
                }
            }

            if (equalCellsBitstreamSize == -1)
            {
                equalCellsBitstream =
                        new BitInputStream(
                                new PatternGeneratedInputStream(new byte[1024]), BitOrder.LOWEST_BIT_FIRST,
                                EndianFormat.LITTLE_ENDIAN);
                equalCellsBitstream2 = equalCellsBitstream;
            } else
            {
                byte[] buffer = bis.readBitsAsArray(equalCellsBitstreamSize);
                equalCellsBitstream = new BitLimitedInputStream(
                        new ByteArrayInputStream(buffer), bis.getBitOrder(), bis.getEndianFormat(),
                        equalCellsBitstreamSize);
                equalCellsBitstream2 = new BitLimitedInputStream(
                        new ByteArrayInputStream(buffer), bis.getBitOrder(), bis.getEndianFormat(),
                        equalCellsBitstreamSize);
                // DEBUG: Used to examine the contents of the equal_cells_bitstream
//                System.err.println("EqualCellsBitstream contents:");
//                System.err.print(StringUtilities.createFormattedHexDumpString(new ByteArrayInputStream(buffer)));
            }

            pixelMaskBitstream = bis.getBufferedStream(pixelMaskBitstreamSize);
            if (encodingTypeBistreamSize == -1)
            {
                encodingTypeBitstream = new BitInputStream(
                        new PatternGeneratedInputStream(new byte[1024]),
                        BitOrder.HIGHEST_BIT_FIRST, EndianFormat.LITTLE_ENDIAN);
            } else
            {
                encodingTypeBitstream = bis.getBufferedStream(encodingTypeBistreamSize);
            }

            if (rawPixelCodesBitstreamSize == -1)
            {
                rawPixelCodesBitstream = new BitInputStream(
                        new PatternGeneratedInputStream(new byte[1024]),
                        BitOrder.HIGHEST_BIT_FIRST, EndianFormat.LITTLE_ENDIAN);
            } else
            {
                rawPixelCodesBitstream = bis.getBufferedStream(rawPixelCodesBitstreamSize);
            }

            // All bitstreams have been buffered.  Prepare to decode.
            // Pre-decoding analysis:
            int frameBufferMinX = Integer.MAX_VALUE; // inclusive
            int frameBufferMaxX = Integer.MIN_VALUE; // exclusive
            int frameBufferMinY = Integer.MAX_VALUE; // inclusive
            int frameBufferMaxY = Integer.MIN_VALUE; // exclusive
            for (int frame = 0; frame < frames; frame++)
            {
                frameBufferMinX = Math.min(frameBufferMinX, frameHeaders[frame].getXOffset());
                frameBufferMaxX =
                        Math.max(
                                frameBufferMaxX,
                                frameHeaders[frame].getXOffset() + frameHeaders[frame].getWidth());
                frameBufferMinY = Math.min(frameBufferMinY, frameHeaders[frame].getYOffset());
                frameBufferMaxY =
                        Math.max(
                                frameBufferMaxY,
                                frameHeaders[frame].getYOffset() + frameHeaders[frame].getHeight());
            }

            // ********** PHASE 1: DECODE PALETTES **********
            int frameBufferWidth = frameBufferMaxX - frameBufferMinX;
            int frameBufferHeight = frameBufferMaxY - frameBufferMinY;
            DCCFrameBufferPalette[][][] frameBufferCellPalettes =
                    new DCCFrameBufferPalette[frames][(frameBufferHeight + 3) / 4][(frameBufferWidth + 3) / 4];

            for (int frame = 0; frame < frames; frame++)
            {
                DCCFrameCellContext cellContext =
                        new DCCFrameCellContext(frameBufferMinX, frameBufferMinY, frameHeaders[frame]);

                for (int y = cellContext.getFrameCellTopIndex(); y <= cellContext.getFrameCellBottomIndex(); y++)
                {
                    for (int x = cellContext.getFrameCellLeftIndex();
                         x <= cellContext.getFrameCellRightIndex(); x++)
                    {
                        // For each cell of each frame...

                        // Find the most recent palette for this specific cell.
                        DCCFrameBufferPalette palette;
                        int lastPaletteEntry = frame - 1;
                        while ((lastPaletteEntry >= 0) &&
                               (frameBufferCellPalettes[lastPaletteEntry][y][x] == null))
                        {
                            lastPaletteEntry--;
                        }
                        boolean previouslyDecoded = (lastPaletteEntry >= 0);
                        if (lastPaletteEntry < 0)
                        {
                            palette = new DCCFrameBufferPalette();
                        } else
                        {
                            palette = frameBufferCellPalettes[lastPaletteEntry][y][x].copy();
                        }

                        // Determine if the frame has been changed at all since the last palette entry.
                        if ((!previouslyDecoded) || (!equalCellsBitstream.readBoolean()))
                        {
                            // This frame is -not- identical to the last one.
                            int pixelMask;
                            if (previouslyDecoded)
                            {
                                pixelMask = pixelMaskBitstream.readBits(4);
                            } else
                            {
                                pixelMask = 0xF;
                            }

                            if (pixelMask != 0)
                            {
                                int lastDecoded = 0;
                                boolean rawPixelEncoding = encodingTypeBitstream.readBoolean();
                                Stack<Byte> pixelValueStack = new Stack<Byte>();
                                int pixelCount = 0;
                                while (pixelCount < MathUtilities.countSetBits(pixelMask))
                                {
                                    int before = lastDecoded;
                                    if (rawPixelEncoding)
                                    {
                                        lastDecoded = rawPixelCodesBitstream.readBits(8);
                                    } else
                                    {
                                        int inc;
                                        do
                                        {
                                            inc = bis.readBits(4);
                                            lastDecoded += inc;
                                            lastDecoded %= 256;
                                        } while (inc == 15);
                                    }
                                    if (lastDecoded == before)
                                    {
                                        break;
                                    } else
                                    {
                                        pixelCount++;
                                        pixelValueStack.push(pixelValuesMapping[lastDecoded]);
                                    }
                                }

                                int paletteIndex = 0;
                                while (pixelMask != 0)
                                {
                                    if ((pixelMask & 0x1) != 0)
                                    {
                                        byte color = (pixelValueStack.size() > 0) ?
                                                     pixelValueStack.pop() : (byte) 0;
                                        palette.setColor(paletteIndex, color);
                                    }
                                    pixelMask >>= 1;
                                    paletteIndex++;
                                }
                            }
                        }

                        // Use the new palette in the buffer for this frame.
                        frameBufferCellPalettes[frame][y][x] = palette;
                    }
                }
            }

            // ********** PHASE 2: DECODE FRAMES **********
            byte[][] pixelData = new byte[frameBufferHeight][frameBufferWidth];
            //noinspection MismatchedReadAndWriteOfArray
            DCCFrameBufferCell[][] frameBufferCells =
                    new DCCFrameBufferCell[(frameBufferHeight + 3) / 4][(frameBufferWidth + 3) / 4];
            boolean[][] previouslyDecodedFrame =
                    new boolean[(frameBufferHeight + 3) / 4][(frameBufferWidth + 3) / 4];

            for (DCCFrameBufferCell[] arr : frameBufferCells)
            {
                for (int i = 0; i < arr.length; i++)
                {
                    arr[i] = new DCCFrameBufferCell();
                }
            }

            for (int frame = 0; frame < frames; frame++)
            {
                DCCFrameCellContext cellContext =
                        new DCCFrameCellContext(frameBufferMinX, frameBufferMinY, frameHeaders[frame]);

                for (int y = cellContext.getFrameCellTopIndex(); y <= cellContext.getFrameCellBottomIndex(); y++)
                {
                    // Determine frame cell dimensions and positions
                    int frameCellHeight = cellContext.getFrameCellHeight(y);
                    int frameCellYOffset = cellContext.getFrameCellYOffset(y);

                    for (int x = cellContext.getFrameCellLeftIndex();
                         x <= cellContext.getFrameCellRightIndex(); x++)
                    {
                        // Determine frame cell dimensions and positions
                        int frameCellWidth = cellContext.getFrameCellWidth(x);
                        int frameCellXOffset = cellContext.getFrameCellXOffset(x);

                        // Now determine if this cell was decoded in a previous frame
                        if ((previouslyDecodedFrame[y][x]) && (equalCellsBitstream2.readBoolean()))
                        {
                            // This cell matches the old one or is transparent (depending on cell status)
                            // Does this frame cell match the layout of the last frame cell associated with this frame
                            // buffer cell?
                            if ((frameCellHeight == frameBufferCells[y][x].getHeight()) &&
                                (frameCellWidth == frameBufferCells[y][x].getWidth()) &&
                                (frameCellXOffset == frameBufferCells[y][x].getXOffset()) &&
                                (frameCellYOffset == frameBufferCells[y][x].getYOffset()))
                            {
                                // The pixel data for this frame cell is identical to the old pixel data.
                            } else
                            {
                                // The cell is transparent.
                                for (int ypx = 0; ypx < 4; ypx++)
                                {
                                    for (int xpx = 0; xpx < 4; xpx++)
                                    {
                                        int ypos = ypx + 4 * y;
                                        int xpos = xpx + 4 * x;
                                        if ((ypos < pixelData.length) &&
                                            (xpos < pixelData[ypos].length))
                                        {
                                            pixelData[ypos][xpos] = transparentIndex;
                                        }
                                    }
                                }
                            }
                        } else
                        {
                            // This cell must be explicitly decoded
                            DCCFrameBufferPalette palette = frameBufferCellPalettes[frame][y][x];
                            for (int ypx = 4 * y + frameCellYOffset;
                                 ypx < 4 * y + frameCellYOffset + frameCellHeight; ypx++)
                            {
                                for (int xpx = 4 * x + frameCellXOffset;
                                     xpx < 4 * x + frameCellXOffset + frameCellWidth; xpx++)
                                {
                                    pixelData[ypx][xpx] = palette.getColor(bis.readBits(palette.getColorBits()));
                                }
                            }
                            frameBufferCells[y][x].setWidth(frameCellWidth);
                            frameBufferCells[y][x].setHeight(frameCellHeight);
                            frameBufferCells[y][x].setXOffset(frameCellXOffset);
                            frameBufferCells[y][x].setYOffset(frameCellYOffset);
                        }
                        previouslyDecodedFrame[y][x] = true;
                    }
                }

                // Frame decode complete... copy and store the image.
                BufferedImage image = new BufferedImage(
                        Math.max(1, frameHeaders[frame].getWidth()),
                        Math.max(1, frameHeaders[frame].getHeight()),
                        BufferedImage.TYPE_INT_ARGB);
                if (frameHeaders[frame].isBottomUp())
                {
                    for (int y = 0; y < frameHeaders[frame].getHeight(); y++)
                    {
                        for (int x = 0; x < frameHeaders[frame].getWidth(); x++)
                        {
                            int frameBufferRelX = frameHeaders[frame].getXOffset() - frameBufferMinX;
                            int frameBufferRelY = frameBufferHeight -
                                                  (frameHeaders[frame].getYOffset() - frameBufferMinY) - 1;
                            image.setRGB(
                                    x, y,
                                    model.getRGB(pixelData[frameBufferRelY + y][frameBufferRelX + x] & 0xFF));
                        }
                    }
                } else
                {
                    for (int y = 0; y < frameHeaders[frame].getHeight(); y++)
                    {
                        for (int x = 0; x < frameHeaders[frame].getWidth(); x++)
                        {
                            int frameBufferRelX = frameHeaders[frame].getXOffset() - frameBufferMinX;
                            int frameBufferRelY = frameHeaders[frame].getYOffset() - frameBufferMinY;
                            image.setRGB(
                                    x, y,
                                    model.getRGB(pixelData[frameBufferRelY + y][frameBufferRelX + x] & 0xFF));
                        }
                    }
                }

                // TODO: add some kind of configuration option for whether or not SixDice respects bottom-up Y-offsets
                // Currently, SixDice translates all offsets to top-left corner values.
                ret[frame] = new AnimationFrame(
                        image, frameHeaders[frame].getXOffset(), frameHeaders[frame].getYOffset());
                if (frameHeaders[frame].getOptionalData() != null)
                {
                    ret[frame].setOptionalData(frameHeaders[frame].getOptionalData());
                }
            }

            return Arrays.asList(ret);
        } catch (EOFException eofe)
        {
            throw new ParseException("Direction decode failed: Unexpected end of bitstream.", eofe);
        }
    }

    /**
     * Encodes an {@link Animation} in DCC format.  This method should not be called unless a call to {@link
     * AnimationCodec#check(Animation)} using the same {@link Animation} object produces no messages with a {@link
     * orioni.sixdice.AnimationCodec.MessageType#FATAL} type.
     *
     * @param animation The {@link Animation} to write.
     * @param palette   The palette in which to write the DCC file.
     * @param tracker   The {@link ProgressTracker} which tracks the progress of this operation.
     * @return The encoded DCC data.
     */
    public byte[] encode(Animation animation, RestrictableIndexColorModel palette, ProgressTracker tracker)
    {
        ByteArrayOutputStream encodingBuffer = new ByteArrayOutputStream();

        palette = deriveCodecPalette(palette);

        // buffer the OutSizeCoded value for each frame, since it's such a weighty calculation
        int[] outSizeCodedValues = new int[animation.getDirectionCount()];
        int[][] codedFrameValues = new int[animation.getDirectionCount()][animation.getFrameCount()];
        int totalSizeCoded = 24 + 4 * animation.getDirectionCount() * animation.getFrameCount();
        for (int d = 0; d < codedFrameValues.length; d++)
        {
            int outSizeCoded = 0;
            for (int f = 0; f < codedFrameValues[d].length; f++)
            {
                // TODO: replace this routine with an estimation?
                codedFrameValues[d][f] = DC6Codec.encodeFrame(animation, d, f, palette).length;
                outSizeCoded += codedFrameValues[d][f];
            }
            outSizeCoded += 35 * animation.getFrameCount();
            outSizeCodedValues[d] = outSizeCoded;
            totalSizeCoded += outSizeCoded;
        }

        // DEBUG: The following statement describes the TotalSizeCoded of the DCC being written.
        //System.err.println("Writing DCC with TotalSizeCoded value of " + total_size_coded);

        byte[][] encodedDirectionData = new byte[animation.getDirectionCount()][];
        for (int i = 0; i < animation.getDirectionCount(); i++)
        {
            encodedDirectionData[i] = encodeDirection(
                    animation, i, true, true, outSizeCodedValues[i], codedFrameValues[i], palette,
                    tracker.getSubtrackerByPercentage(25));

            byte[] temp = encodeDirection(
                    animation, i, true, false, outSizeCodedValues[i], codedFrameValues[i], palette,
                    tracker.getSubtrackerByPercentage(25));
            if (temp.length < encodedDirectionData[i].length)
            {
                encodedDirectionData[i] = temp;
            }

            temp = encodeDirection(
                    animation, i, false, true, outSizeCodedValues[i], codedFrameValues[i], palette,
                    tracker.getSubtrackerByPercentage(25));
            if (temp.length < encodedDirectionData[i].length)
            {
                encodedDirectionData[i] = temp;
            }

            temp = encodeDirection(
                    animation, i, false, false, outSizeCodedValues[i], codedFrameValues[i], palette,
                    tracker.getSubtrackerByPercentage(25));
            if (temp.length < encodedDirectionData[i].length)
            {
                encodedDirectionData[i] = temp;
            }
        }

        try
        {
            PrimitiveOutputStream pos = new PrimitiveOutputStream(
                    encodingBuffer, PrimitiveOutputStream.LITTLE_ENDIAN);
            pos.writeUnsignedByte((byte) 0x74);                              // DCC signature byte
            pos.writeUnsignedByte((byte) 0x06);                              // DCC version identifier
            pos.writeUnsignedByte((byte) (animation.getDirectionCount()));   // direction count
            pos.writeInt(animation.getFrameCount());                         // frame count
            pos.writeInt(1);                                                 // one - see DCC spec
            pos.writeInt(totalSizeCoded);                                  // information for prebuffering readers
            // Time to write the offset table.  The size of the above header is 15 bytes... then add the offset table
            // itself.
            int currentOffset = 15 + 4 * animation.getDirectionCount();
            for (int i = 0; i < animation.getDirectionCount(); i++)
            {
                pos.writeInt(currentOffset);
                currentOffset += encodedDirectionData[i].length;
            }
            // Now write the data
            for (byte[] data : encodedDirectionData)
            {
                encodingBuffer.write(data);
            }
            pos.close();

            // Looks like we're finished.  Looks clean when you package all that stuff in a method or two, huh?
            // >::::D  <--- happy evil spider
        } catch (IOException ioe)
        {
            // This can't happen unless ByteArrayOutputStream throws an IOException
            throw new IllegalStateException("ByteArrayOutputStream threw an IOException!", ioe);
        }
        tracker.setProgressCompleted();
        return encodingBuffer.toByteArray();
    }

    /**
     * Encodes the specified direction of the provided {@link Animation} object using the compression techniques
     * described.  As there are only four combinations of compression techniques, it is feasible for a calling method
     * (such as {@link DCCCodec#encode(Animation, orioni.jz.awt.image.RestrictableIndexColorModel, ProgressTracker)} to
     * generate each <code>byte[]</code> and then determine which is smallest.
     *
     * @param animation        The {@link Animation} with the direction to encode.
     * @param direction        The index of the direction to encode.
     * @param compressionFlagA <code>true</code> if the equal cells bitstream will be present in this encoding;
     *                         <code>false</code> otherwise.
     * @param compressionFlagB <code>true</code> if the raw pixel codes and encoding type bitstreams will be present in
     *                         this encoding.
     * @param outSizeCoded     The OutSizeCoded value for this direction.  This value is passed as a parameter to avoid
     *                         determining the OutSizeCoded more than once for each direction, as this is a costly
     *                         operation.
     * @param codedFrameSizes  The coded frame sizes for this direction.    This value is passed as a parameter to avoid
     *                         determining the sizes more than once for each direction, as this is a costly operation.
     * @param tracker          The {@link ProgressTracker} which tracks the progress of this encoding sequence.
     * @return The DCC-encoded data.
     */
    private byte[] encodeDirection(Animation animation, int direction, boolean compressionFlagA,
                                   boolean compressionFlagB, int outSizeCoded, int[] codedFrameSizes,
                                   RestrictableIndexColorModel animationPalette, ProgressTracker tracker)
    {
        ProgressTracker ditherTracker = tracker.getSubtrackerByPercentage(0, animation.getFrameCount(), 50);
        ProgressTracker encodeTracker = tracker.getSubtrackerByPercentage(0, animation.getFrameCount(), 50);
        // TODO: move or remove these compression-disabling statements
//        compression_flag_a = false;
//        compression_flag_b = false;

        int transparentIndex = animationPalette.getMostTransparentIndex();
        Color transparentColor = new Color(animationPalette.getRGB(transparentIndex), true);

        try
        {
            ByteArrayOutputStream baos =
                    new ByteArrayOutputStream(); // the buffer into which we write finalized DCC data
            PrimitiveOutputStream pos = new PrimitiveOutputStream(baos, PrimitiveOutputStream.LITTLE_ENDIAN);
            pos.writeInt(outSizeCoded);

            BitOutputStream bos = new BitOutputStream(baos, BitOrder.LOWEST_BIT_FIRST, EndianFormat.LITTLE_ENDIAN);
            bos.writeBit(compressionFlagB);
            bos.writeBit(compressionFlagA);

            // Establish significant bits for each field
            int variable0BitsCode = 0;
            int widthBitsCode = 0;
            int heightBitsCode = 0;
            int xOffsetBitsCode = 0;
            int yOffsetBitsCode = 0;
            int optionalDataBitsCode = 0; // add support for optional frame data
            int codedBytesBitsCode = 0;
            // Write direction data bitstream header
            for (int frameIndex = 0; frameIndex < animation.getFrameCount(); frameIndex++)
            {
                AnimationFrame frame = animation.getFrame(direction, frameIndex);
                widthBitsCode = Math.max(widthBitsCode, getCompressionBitCode(frame.getImage().getWidth(), false));
                heightBitsCode =
                        Math.max(heightBitsCode, getCompressionBitCode(frame.getImage().getHeight(), false));
                xOffsetBitsCode = Math.max(xOffsetBitsCode, getCompressionBitCode(frame.getXOffset(), true));
                // note that Y offset should be from the bottom left corner... Animations store it from the top left
                yOffsetBitsCode =
                        Math.max(
                                yOffsetBitsCode,
                                getCompressionBitCode(frame.getYOffset() + frame.getImage().getHeight() - 1, true));
                optionalDataBitsCode =
                        Math.max(optionalDataBitsCode, getCompressionBitCode(frame.getOptionalData().length, false));
                codedBytesBitsCode = Math.max(
                        codedBytesBitsCode,
                        getCompressionBitCode(codedFrameSizes[frameIndex], false));
            }
            bos.writeBits(variable0BitsCode, 4);
            bos.writeBits(widthBitsCode, 4);
            bos.writeBits(heightBitsCode, 4);
            bos.writeBits(xOffsetBitsCode, 4);
            bos.writeBits(yOffsetBitsCode, 4);
            bos.writeBits(optionalDataBitsCode, 4);
            bos.writeBits(codedBytesBitsCode, 4);

            int frameBufferMinX = Integer.MAX_VALUE; // inclusive
            int frameBufferMinY = Integer.MAX_VALUE; // inclusive
            int frameBufferMaxX = Integer.MIN_VALUE; // exclusive
            int frameBufferMaxY = Integer.MIN_VALUE; // exclusive
            // Write frame headers (and establish frame buffer size).
            for (int frameIndex = 0; frameIndex < animation.getFrameCount(); frameIndex++)
            {
                AnimationFrame frame = animation.getFrame(direction, frameIndex);
                // yeah, this next line does nothing... but it's good for consistency
                bos.writeBits(0, DCC_SIZE_COMPRESSION_FUNCTION[variable0BitsCode]);
                bos.writeBits(frame.getImage().getWidth(), DCC_SIZE_COMPRESSION_FUNCTION[widthBitsCode]);
                bos.writeBits(frame.getImage().getHeight(), DCC_SIZE_COMPRESSION_FUNCTION[heightBitsCode]);
                bos.writeBitsSigned(frame.getXOffset(), DCC_SIZE_COMPRESSION_FUNCTION[xOffsetBitsCode]);
                // note that Y offset should be from the bottom left corner... Animations store it from the top left
                bos.writeBitsSigned(
                        frame.getYOffset() + frame.getImage().getHeight() - 1,
                        DCC_SIZE_COMPRESSION_FUNCTION[yOffsetBitsCode]);
                bos.writeBits(frame.getOptionalData().length, DCC_SIZE_COMPRESSION_FUNCTION[optionalDataBitsCode]);
                bos.writeBits(codedFrameSizes[frameIndex], DCC_SIZE_COMPRESSION_FUNCTION[codedBytesBitsCode]);
                bos.writeBit(false); // Frame are *not* written bottom-up. That's just silly

                // establish frame buffer size
                frameBufferMinX = Math.min(frameBufferMinX, frame.getXOffset());
                frameBufferMinY = Math.min(frameBufferMinY, frame.getYOffset());
                frameBufferMaxX = Math.max(frameBufferMaxX, frame.getXOffset() + frame.getImage().getWidth());
                frameBufferMaxY = Math.max(frameBufferMaxY, frame.getYOffset() + frame.getImage().getHeight());
            }

            final int frameBufferWidth = frameBufferMaxX - frameBufferMinX;
            final int frameBufferHeight = frameBufferMaxY - frameBufferMinY;

            if (optionalDataBitsCode > 0)
            {
                bos.flush();
                for (int frameIndex = 0; frameIndex < animation.getFrameCount(); frameIndex++)
                {
                    bos.write(animation.getFrame(direction, frameIndex).getOptionalData());
                }
            }

            // Frame header complete.  The size of the data bitstreams are written next.  This means that we have to
            // finish everything else before we can do any more writes.

            // Establish buffering bitstreams
            BitBufferOutputStream equalCellsBitstream = null;
            BitBufferOutputStream pixelMaskBitstream;
            BitBufferOutputStream encodingTypeBitstream = null;
            BitBufferOutputStream rawPixelCodesBitstream = null;
            BitBufferOutputStream displacementBitstream;
            BitBufferOutputStream pixelCodesBitstream;
            if (compressionFlagA)
            {
                equalCellsBitstream = new BitBufferOutputStream(
                        BitOrder.LOWEST_BIT_FIRST, EndianFormat.LITTLE_ENDIAN);
            }
            pixelMaskBitstream = new BitBufferOutputStream(BitOrder.LOWEST_BIT_FIRST, EndianFormat.LITTLE_ENDIAN);
            if (compressionFlagB)
            {
                rawPixelCodesBitstream = new BitBufferOutputStream(
                        BitOrder.LOWEST_BIT_FIRST, EndianFormat.LITTLE_ENDIAN);
                encodingTypeBitstream = new BitBufferOutputStream(
                        BitOrder.LOWEST_BIT_FIRST, EndianFormat.LITTLE_ENDIAN);
            }
            displacementBitstream = new BitBufferOutputStream(BitOrder.LOWEST_BIT_FIRST, EndianFormat.LITTLE_ENDIAN);
            pixelCodesBitstream = new BitBufferOutputStream(BitOrder.LOWEST_BIT_FIRST, EndianFormat.LITTLE_ENDIAN);

            // Establish frame buffer
            byte[][] frameBufferPixels = new byte[frameBufferHeight][frameBufferWidth];
            DCCFrameBufferPalette[][] frameBufferPaletteBuffer =
                    new DCCFrameBufferPalette[(frameBufferHeight + 3) / 4][(frameBufferWidth + 3) / 4];
            DCCFrameBufferCell[][] frameBufferCells =
                    new DCCFrameBufferCell[(frameBufferHeight + 3) / 4][(frameBufferWidth + 3) / 4];
            boolean[][] previouslyEncodedFrame =
                    new boolean[(frameBufferHeight + 3) / 4][(frameBufferWidth + 3) / 4];
            DCCFrameBufferPalette[][][] framePalettes =
                    new DCCFrameBufferPalette[animation.getFrameCount()]
                            [(frameBufferHeight + 3) / 4][(frameBufferWidth + 3) / 4];
            BufferedImage[][][] frameSlices =
                    new BufferedImage[animation.getFrameCount()]
                            [(frameBufferHeight + 3) / 4][(frameBufferWidth + 3) / 4];

            for (DCCFrameBufferCell[] arr : frameBufferCells)
            {
                for (int i = 0; i < arr.length; i++)
                {
                    arr[i] = new DCCFrameBufferCell();
                }
            }

            // Establish color model reverse mapping
            Map<Color, Byte> colorModelReverseMapping =
                    new DefaultValueHashMap<Color, Byte>((byte) transparentIndex);
            Map<Integer, Byte> rgbModelReverseMapping = new HashMap<Integer, Byte>();
            for (int i = animationPalette.getMapSize() - 1; i >= 0; i--)
            {
                if (animationPalette.isValid(i))
                {
                    int rgb = animationPalette.getRGB(i);
                    colorModelReverseMapping.put(new Color(rgb, true), (byte) i);
                    rgbModelReverseMapping.put(rgb, (byte) i);
                }
            }

            // Note that the encoding process does not need to be multiphase
            // ********** PERFORM ENCODING PROCESS **********
            // First, dither the frames
            BitMap pixelValuesKey = new BitMap(256);
            for (int frameIndex = 0; frameIndex < animation.getFrameCount(); frameIndex++)
            {
                AnimationFrame frame = animation.getFrame(direction, frameIndex);
                DCCFrameCellContext cellContext =
                        new DCCFrameCellContext(frameBufferMinX, frameBufferMinY, frame);

                int yoffset = 0; // used to determine the yoffset in the image of the current cell
                BufferedImage imageCopy = animationPalette.redraw(frame.getImage());

                for (int y = cellContext.getFrameCellTopIndex(); y <= cellContext.getFrameCellBottomIndex(); y++)
                {
                    // Establish frame dimensions and position
                    int frameCellHeight = cellContext.getFrameCellHeight(y);

                    int xoffset = 0; // used to determine the xoffset in the image of the current cell

                    for (int x = cellContext.getFrameCellLeftIndex();
                         x <= cellContext.getFrameCellRightIndex(); x++)
                    {
                        // Establish frame dimensions and position
                        int frameCellWidth = cellContext.getFrameCellWidth(x);

                        // Establish frame cell image
                        BufferedImage slice = imageCopy.getSubimage(
                                xoffset, yoffset, frameCellWidth, frameCellHeight);
                        frameSlices[frameIndex][y][x] = slice;

                        // Establish and write color_set for this frame
                        Set<Color> colorSet = ImageUtilities.ditherImage(
                                slice, 4, true, TransparencyCriticizingSampleDifferenceComparator.SINGLETON,
                                transparentColor);
                        colorSet.remove(transparentColor);
                        int index = 0;
                        DCCFrameBufferPalette palette = new DCCFrameBufferPalette();
                        for (Color c : colorSet)
                        {
                            palette.setColor(index++, colorModelReverseMapping.get(c));
                        }
                        while (index < 4)
                        {
                            palette.setColor(index++, (byte) transparentIndex);
                        }
                        palette.sortSamples((byte) transparentIndex);

                        for (int i = 0; i < 4; i++)
                        {
                            pixelValuesKey.setBit(palette.getColor(i) & 0xFF, true);
                        }

                        framePalettes[frameIndex][y][x] = palette;

                        xoffset += frameCellWidth;
                    }
                    yoffset += frameCellHeight;
                }

                ditherTracker.incrementProgress(1);
            }

            // Maps the pixel values (sample values in the IndexColorModel) to pixel codes (see pixel_values_key)
            Map<Byte, Integer> pixelCodesMapping = new HashMap<Byte, Integer>();
            int pixelCodeIndex = 0;
            for (int i = 0; i < 256; i++)
            {
                if (pixelValuesKey.getBit(i))
                {
                    pixelCodesMapping.put((byte) i, pixelCodeIndex++);
                }
            }

            // Now encode frames
            for (int frameIndex = 0; frameIndex < animation.getFrameCount(); frameIndex++)
            {
                AnimationFrame frame = animation.getFrame(direction, frameIndex);
                DCCFrameCellContext cellContext =
                        new DCCFrameCellContext(frameBufferMinX, frameBufferMinY, frame);

                for (int y = cellContext.getFrameCellTopIndex(); y <= cellContext.getFrameCellBottomIndex(); y++)
                {
                    // Establish frame dimensions and position
                    int frameCellHeight = cellContext.getFrameCellHeight(y);
                    int frameCellYOffset = cellContext.getFrameCellYOffset(y);

                    for (int x = cellContext.getFrameCellLeftIndex();
                         x <= cellContext.getFrameCellRightIndex(); x++)
                    {
                        // Establish frame dimensions and position
                        int frameCellWidth = cellContext.getFrameCellWidth(x);
                        int frameCellXOffset = cellContext.getFrameCellXOffset(x);

                        // BEGIN ACTUAL ENCODE AND WRITE PROCESS
                        BufferedImage slice = frameSlices[frameIndex][y][x];
                        DCCFrameBufferPalette palette = framePalettes[frameIndex][y][x];

                        // WRITE STEP 1: EVALUATE EQUAL CELLS BITSTREAM
                        boolean cellIdentical = false;
                        if ((compressionFlagA) && (previouslyEncodedFrame[y][x]))
                        {
                            // Check if this frame_buffer_cell is identical to the last one
                            DCCFrameBufferCell frameBufferCell = frameBufferCells[y][x];
                            if ((frameBufferCell.getWidth() == frameCellWidth) &&
                                (frameBufferCell.getHeight() == frameCellHeight) &&
                                (frameBufferCell.getXOffset() == frameCellXOffset) &&
                                (frameBufferCell.getYOffset() == frameCellYOffset))
                            {
                                // Is the frame cell identical to the last one?
                                cellIdentical = true;
                                for (int yidx = 0; yidx < frameCellHeight; yidx++)
                                {
                                    for (int xidx = 0; xidx < frameCellWidth; xidx++)
                                    {
                                        if (rgbModelReverseMapping.get(slice.getRGB(xidx, yidx)) !=
                                            frameBufferPixels[y * 4 + yidx][x * 4 + xidx])
                                        {
                                            cellIdentical = false;
                                            break;
                                        }
                                    }
                                    if (!cellIdentical) break;
                                }
                            } else
                            {
                                // TODO: What exactly does a "transparent cell" mean?
                                // Which cell?  Frame buffer cell?  Frame cell?
                                // Currently, the frame buffer cell is assumed because of problems with an assassin
                                // cast overlay from Diablo II... however, CV52 disagrees in some cases.

                                // Is the frame cell completely transparent?
                                // Note: Since the clear operation specifies to clear a 4x4 frame buffer cell, this
                                // method cannot be used on frame cells with either dimension of 5.
                                if ((frameCellWidth < 5) && (frameCellHeight < 5))
                                {
                                    cellIdentical = true;
                                    for (int yidx = 0; yidx < frameCellHeight; yidx++)
                                    {
                                        for (int xidx = 0; xidx < frameCellWidth; xidx++)
                                        {
                                            if (rgbModelReverseMapping.get(slice.getRGB(xidx, yidx)) !=
                                                transparentIndex)
                                            {
                                                cellIdentical = false;
                                                break;
                                            }
                                        }
                                    }
                                    if (cellIdentical)
                                    {
                                        for (int yidx = 0; yidx < frameCellHeight; yidx++)
                                        {
                                            for (int xidx = 0; xidx < frameCellWidth; xidx++)
                                            {
                                                int ypos = yidx + 4 * y;
                                                int xpos = xidx + 4 * x;
                                                if ((ypos < frameBufferPixels.length) &&
                                                    (xpos < frameBufferPixels[ypos].length))
                                                {
                                                    frameBufferPixels[ypos][xpos] = (byte) transparentIndex;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (cellIdentical)
                        {
                            equalCellsBitstream.writeBit(true);
                        } else
                        {
                            if ((previouslyEncodedFrame[y][x]) && (equalCellsBitstream != null))
                            {
                                equalCellsBitstream.writeBit(false);
                            }

                            // WRITE STEP 2: ESTABLISH PALETTE, PIXEL MASK, AND ENCODING TYPE
                            boolean encodingType = false;
                            int pixelMask;
                            byte[] paletteValues = palette.getInvertedPaletteValuesArray(transparentIndex);
                            if (previouslyEncodedFrame[y][x])
                            {
                                pixelMask = palette.getPixelMask(frameBufferPaletteBuffer[y][x]);
                            } else
                            {
                                pixelMask = 0xF;
                            }

                            if ((compressionFlagB) && (pixelMask > 0))
                            {
                                int displacementBits = 0;
                                int rawBits;
                                // Establish bits used by displacement approach
                                int lastWritten = 0;
                                for (int i = 0; i < paletteValues.length; i++)
                                {
                                    if (((pixelMask >>> (paletteValues.length - 1 - i)) & 0x1) != 0)
                                    {
                                        int diff = pixelCodesMapping.get(paletteValues[i]) - lastWritten;
                                        displacementBits += 4 * (1 + diff / 15);
                                    }
                                }
                                if ((pixelMask >>> paletteValues.length) != 0)
                                {
                                    displacementBits += 4;
                                }
                                // Establish bits used by raw encoding approach
                                DCCFrameBufferPalette rawPalette;
                                int rawPixelMask;
                                if (previouslyEncodedFrame[y][x])
                                {
                                    rawPalette = palette.copy();
                                    rawPalette.rearrangeToResemble(
                                            frameBufferPaletteBuffer[y][x], transparentIndex);
                                    rawPixelMask = rawPalette.getPixelMask(frameBufferPaletteBuffer[y][x]);
                                } else
                                {
                                    rawPalette = palette;
                                    rawPixelMask = pixelMask;
                                }
                                rawBits = (((pixelMask & 0x8) != 0) ? 8 : 0) +
                                          (((pixelMask & 0x4) != 0) ? 8 : 0) +
                                          (((pixelMask & 0x2) != 0) ? 8 : 0) +
                                          (((pixelMask & 0x1) != 0) ? 8 : 0);
                                // Now decide
                                if (displacementBits > rawBits)
                                {
                                    encodingType = true;
                                    palette = rawPalette;
                                    pixelMask = rawPixelMask;
                                    paletteValues = rawPalette.getInvertedPaletteValuesArray(transparentIndex);
                                }
                            }

                            // WRITE STEP 3: WRITE PALETTE, PIXEL MASK, AND ENCODING TYPE
                            if (previouslyEncodedFrame[y][x]) pixelMaskBitstream.writeBits(pixelMask, 4);
                            if (pixelMask > 0)
                            {
                                if (compressionFlagB) encodingTypeBitstream.writeBit(encodingType);

                                int lastWritten = 0;
                                for (int i = 0; i < paletteValues.length; i++)
                                {
                                    if (((pixelMask >>> (paletteValues.length - 1 - i)) & 0x1) != 0)
                                    {
                                        int pixelCode = pixelCodesMapping.get(paletteValues[i]);
                                        if (encodingType)
                                        {
                                            rawPixelCodesBitstream.writeBits(pixelCode, 8);
                                        } else
                                        {
                                            int diff = pixelCode - lastWritten;
                                            while (diff > 14)
                                            {
                                                displacementBitstream.writeBits(15, 4);
                                                diff -= 15;
                                            }
                                            displacementBitstream.writeBits(diff, 4);
                                        }
                                        lastWritten = pixelCode;
                                    }
                                }
                                if ((pixelMask >>> paletteValues.length) != 0)
                                {
                                    if (encodingType)
                                    {
                                        rawPixelCodesBitstream.writeBits(lastWritten, 8);
                                    } else
                                    {
                                        displacementBitstream.writeBits(0, 4);
                                    }
                                }
                            }
                            frameBufferPaletteBuffer[y][x] = palette;

                            // WRITE STEP 4: ENCODE AND WRITE PIXEL CODES FOR SLICE
                            for (int yidx = 0; yidx < slice.getHeight(); yidx++)
                            {
                                for (int xidx = 0; xidx < slice.getWidth(); xidx++)
                                {
                                    byte sampleValue = rgbModelReverseMapping.get(slice.getRGB(xidx, yidx));
                                    pixelCodesBitstream.writeBits(
                                            palette.findSampleValue(sampleValue), palette.getColorBits());
                                    frameBufferPixels[y * 4 + frameCellYOffset + yidx]
                                            [x * 4 + frameCellXOffset + xidx] = sampleValue;
                                }
                            }

                            DCCFrameBufferCell cell = frameBufferCells[y][x];
                            cell.setWidth(frameCellWidth);
                            cell.setHeight(frameCellHeight);
                            cell.setXOffset(frameCellXOffset);
                            cell.setYOffset(frameCellYOffset);
                        }

                        // Perform post-encoding steps
                        previouslyEncodedFrame[y][x] = true;
                    }
                }

                encodeTracker.incrementProgress(1);
            }

            if (compressionFlagA) bos.writeBits(equalCellsBitstream.bitsWritten(), 20);
            bos.writeBits(pixelMaskBitstream.bitsWritten(), 20);
            if (compressionFlagB)
            {
                bos.writeBits(encodingTypeBitstream.bitsWritten(), 20);
                bos.writeBits(rawPixelCodesBitstream.bitsWritten(), 20);
            }

            bos.write(pixelValuesKey.getBitmap());

            if (compressionFlagA)
            {
                bos.writeBitsFromArray(
                        equalCellsBitstream.toByteArray(),
                        equalCellsBitstream.bitsWritten());
            }
            bos.writeBitsFromArray(
                    pixelMaskBitstream.toByteArray(),
                    pixelMaskBitstream.bitsWritten());
            if (compressionFlagB)
            {
                bos.writeBitsFromArray(
                        encodingTypeBitstream.toByteArray(),
                        encodingTypeBitstream.bitsWritten());
                bos.writeBitsFromArray(
                        rawPixelCodesBitstream.toByteArray(),
                        rawPixelCodesBitstream.bitsWritten());
            }
            bos.writeBitsFromArray(
                    displacementBitstream.toByteArray(),
                    displacementBitstream.bitsWritten());
            bos.writeBitsFromArray(
                    pixelCodesBitstream.toByteArray(),
                    pixelCodesBitstream.bitsWritten());

            bos.close();

            return baos.toByteArray();
        } catch (IOException e)
        {
            // ByteArrayOutputStream just threw an IOException...
            throw new IllegalStateException("ByteArrayOutputStream threw an IOException!", e);
        }
    }

    /**
     * Gets the bit code representing the number of bits necessary to represent the provided value.
     *
     * @param value  The value to represent.
     * @param signed <code>true</code> if the value will need to be stored as a signed variable; <code>false</code> if
     *               it will be stored as an unsigned variable.
     * @return The bit code indicating the number of bits to be used in representing that value.
     */
    private int getCompressionBitCode(int value, boolean signed)
    {
        int sigBits = MathUtilities.countSignificantBits(Math.abs(value));
        if (signed)
        {
            if ((value < 0) && (MathUtilities.countSetBits(Math.abs(value)) == 1))
            {
                // The value is a negative power of 2, meaning that it can actually fit in one less bit
                // Ex.: signed 8 bits allows [-4,3]; -4 is a negative power of 2.
            } else
            {
                sigBits++; // for the sign
            }
        }
        while (DCC_SIZE_COMPRESSION_FUNCTION_INVERTED[sigBits] == -1)
        {
            sigBits++;
        }
        return DCC_SIZE_COMPRESSION_FUNCTION_INVERTED[sigBits];
    }

// CONTAINED CLASSES /////////////////////////////////////////////////////////////

    /**
     * This class is designed to contain information about a DCC frame's cells.
     *
     * @author Zachary Palmer
     */
    static class DCCFrameCellContext
    {
        /**
         * The vertical index of the frame buffer cell which corresponds to the frame's top cells.
         */
        protected final int frameCellTopIndex;
        /**
         * The vertical index of the frame buffer cell which corresponds to the frame's bottom cells.
         */
        protected final int frameCellBottomIndex;
        /**
         * The horizontal index of the frame buffer cell which corresponds to the frame's left cells.
         */
        protected final int frameCellLeftIndex;
        /**
         * The horizontal index of the frame buffer cell which corresponds to the frame's right cells.
         */
        protected final int frameCellRightIndex;
        /**
         * The height of the top frame cells in the frame.
         */
        protected final int topFrameCellHeight;
        /**
         * The height of the bottom frame cells in the frame.
         */
        protected final int bottomFrameCellHeight;
        /**
         * The width of the left frame cells in the frame.
         */
        protected final int leftFrameCellWidth;
        /**
         * The width of the right frame cells in the frame.
         */
        protected final int rightFrameCellWidth;
        /**
         * The offset of the top frame cells from the top of the corresponding frame buffer cell.
         */
        protected final int topFrameCellOffset;
        /**
         * The offset of the left frame cells from the left of the corresponding frame buffer cell.
         */
        protected final int leftFrameCellOffset;

        /**
         * Skeleton constructor.  The provided {@link AnimationFrame} is used to provide information.
         *
         * @param frameBufferMinX The minimum X coordinate in the frame buffer.
         * @param frameBufferMinY The minimum Y coordinate in the frame buffer.
         * @param frame           The {@link AnimationFrame} for which information should be collected.
         */
        public DCCFrameCellContext(int frameBufferMinX, int frameBufferMinY, AnimationFrame frame)
        {
            this(
                    frameBufferMinX, frameBufferMinY, frame.getImage().getWidth(), frame.getImage().getHeight(),
                    frame.getXOffset(), frame.getYOffset());
        }

        /**
         * Skeleton constructor.  The provided {@link DCCFrameHeader} is used to provide information.
         *
         * @param frameBufferMinX The minimum X coordinate in the frame buffer.
         * @param frameBufferMinY The minimum Y coordinate in the frame buffer.
         * @param frameHeader     The {@link DCCFrameHeader} for which information should be collected.
         */
        public DCCFrameCellContext(int frameBufferMinX, int frameBufferMinY, DCCFrameHeader frameHeader)
        {
            this(
                    frameBufferMinX, frameBufferMinY, frameHeader.getWidth(), frameHeader.getHeight(),
                    frameHeader.getXOffset(), frameHeader.getYOffset());
        }

        /**
         * General constructor.
         *
         * @param frameBufferMinX The minimum X coordinate in the frame buffer.
         * @param frameBufferMinY The minimum Y coordinate in the frame buffer.
         * @param frameWidth      The width of the frame.
         * @param frameHeight     The height of the frame.
         * @param frameXOffset    The X offset of the frame.
         * @param frameYOffset    The Y offset of the frame.
         */
        public DCCFrameCellContext(int frameBufferMinX, int frameBufferMinY, int frameWidth, int frameHeight,
                                   int frameXOffset, int frameYOffset)
        {
            frameCellLeftIndex = (frameXOffset - frameBufferMinX) / 4;
            frameCellTopIndex = (frameYOffset - frameBufferMinY) / 4;
            frameCellRightIndex = (frameXOffset - frameBufferMinX + frameWidth - 2) / 4;
            frameCellBottomIndex = (frameYOffset - frameBufferMinY + frameHeight - 2) / 4;

            // If the top frame_index cell height is N and the hieght of the frame_index mod 4 is M, the bottom
            // frame_index cell height is (4-N+M+2) mod 4 + 2.  The only exception is when the height of the frame
            // cell is less than (4-N+M+2) mod 4 + 2.
            topFrameCellHeight = Math.min(4 - (frameYOffset - frameBufferMinY) % 4, frameHeight);
            leftFrameCellWidth = Math.min(4 - (frameXOffset - frameBufferMinX) % 4, frameWidth);
            topFrameCellOffset = (frameYOffset - frameBufferMinY) % 4;
            leftFrameCellOffset = (frameXOffset - frameBufferMinX) % 4;

            int bottomFrameCellHeightTemp = (6 - topFrameCellHeight + (frameHeight % 4)) % 4 + 2;
            if ((bottomFrameCellHeightTemp == 5) && (frameHeight < 5))
            {
                bottomFrameCellHeightTemp = 1;
            }
            bottomFrameCellHeight = Math.min(bottomFrameCellHeightTemp, frameHeight);

            int rightFrameCellWidthTemp = (6 - leftFrameCellWidth + (frameWidth % 4)) % 4 + 2;
            if ((rightFrameCellWidthTemp == 5) && (frameWidth < 5))
            {
                rightFrameCellWidthTemp = 1;
            }
            rightFrameCellWidth = Math.min(rightFrameCellWidthTemp, frameWidth);
        }

        public int getBottomFrameCellHeight()
        {
            return bottomFrameCellHeight;
        }

        public int getFrameCellBottomIndex()
        {
            return frameCellBottomIndex;
        }

        public int getFrameCellLeftIndex()
        {
            return frameCellLeftIndex;
        }

        public int getFrameCellRightIndex()
        {
            return frameCellRightIndex;
        }

        public int getFrameCellTopIndex()
        {
            return frameCellTopIndex;
        }

        public int getLeftFrameCellOffset()
        {
            return leftFrameCellOffset;
        }

        public int getLeftFrameCellWidth()
        {
            return leftFrameCellWidth;
        }

        public int getRightFrameCellWidth()
        {
            return rightFrameCellWidth;
        }

        public int getTopFrameCellHeight()
        {
            return topFrameCellHeight;
        }

        public int getTopFrameCellOffset()
        {
            return topFrameCellOffset;
        }

        /**
         * Retrieves the width of the cell corresponding to the frame buffer cell with the given horizontal index.
         *
         * @param x The horizontal index of the frame buffer cell corresponding to the frame cell in question.  This
         *          value must be between the left and right horizontal indices (inclusive) to produce an accurate
         *          result.
         * @return The width of the frame cell which corresponds to the frame buffer cell with the given index.
         */
        public int getFrameCellWidth(int x)
        {
            if (x == getFrameCellRightIndex())
            {
                return getRightFrameCellWidth();
            } else if (x == getFrameCellLeftIndex())
            {
                return getLeftFrameCellWidth();
            } else
            {
                return 4;
            }
        }

        /**
         * Retrieves the height of the frame cell corresponding to the frame buffer cell with the given vertical index.
         *
         * @param y The vertical index of the frame buffer cell corresponding to the frame cell in question.  This value
         *          must be between the top and bottom vertical indices (inclusive) to produce an accurate result.
         * @return The height of the frame cell which corresponds to the frame buffer cell with the given index.
         */
        public int getFrameCellHeight(int y)
        {
            if (y == getFrameCellBottomIndex())
            {
                return getBottomFrameCellHeight();
            } else if (y == getFrameCellTopIndex())
            {
                return getTopFrameCellHeight();
            } else
            {
                return 4;
            }
        }

        /**
         * Retrives the X offset of the frame cell corresponding to the frame buffer cell with the given horizontal
         * index.
         *
         * @param x The horizontal index of the frame buffer cell corresponding to the frame cell in question.  This
         *          value must be between the left and right vertical indices (inclusive) to produce an accurate
         *          result.
         * @return The distance of the frame cell from the left of the corresponding frame buffer cell with the given
         *         index.
         */
        public int getFrameCellXOffset(int x)
        {
            if (x == getFrameCellLeftIndex())
            {
                return getLeftFrameCellOffset();
            } else
            {
                return 0;
            }
        }

        /**
         * Retrives the Y offset of the frame cell corresponding to the frame buffer cell with the given vertical
         * index.
         *
         * @param y The vertical index of the frame buffer cell corresponding to the frame cell in question.  This value
         *          must be between the top and bottom vertical indices (inclusive) to produce an accurate result.
         * @return The distance of the frame cell from the top of the corresponding frame buffer cell with the given
         *         index.
         */
        public int getFrameCellYOffset(int y)
        {
            if (y == getFrameCellTopIndex())
            {
                return getTopFrameCellOffset();
            } else
            {
                return 0;
            }
        }
    }

    /**
     * This class is designed to represent the palette for a given frame buffer cell in DCC processing.
     *
     * @author Zachary Palmer
     */
    static class DCCFrameBufferPalette
    {
        /**
         * The colors in this palette.
         */
        protected byte[] paletteColors;

        /**
         * General constructor.
         */
        public DCCFrameBufferPalette()
        {
            paletteColors = new byte[4];
        }

        /**
         * Retrieves a color from this palette.
         *
         * @param index The index of the color to retrieve.
         * @return The color in that position.
         * @throws IndexOutOfBoundsException If the provided color index is less than zero or greater than three.
         */
        public byte getColor(int index)
        {
            return paletteColors[index];
        }

        /**
         * Sets a color in this palette.
         *
         * @param index The index ofthe color to set.
         * @param color The new value for this color.
         * @throws IndexOutOfBoundsException If the provided color index is less than zero or greater than three.
         */
        public void setColor(int index, byte color)
        {
            paletteColors[index] = color;
        }

        /**
         * Retrieves the number of "distinct" colors in this palette.  This method assumes that all duplicate colors
         * appear at the end of the palette as per the DCC specification.
         *
         * @return The number of distinct colors in this palette.  Alwaxs a number between <code>1</code> and
         *         <code>4</code>.
         */
        public int getDistinctColorCount()
        {
            if (paletteColors[1] == paletteColors[0]) return 1;
            if (paletteColors[2] == paletteColors[1]) return 2;
            if (paletteColors[3] == paletteColors[2]) return 3;
            return 4;
        }

        /**
         * Retrieves the number of bits which should be used to identifx each color in this palette.
         *
         * @return <code>0</code> if there is onlx one color in this palette, <code>1</code> if there are two, or
         *         <code>2</code> if there are three or four colors in this palette.
         */
        public int getColorBits()
        {
            switch (getDistinctColorCount())
            {
                case 1:
                    return 0;
                case 2:
                    return 1;
                case 3:
                case 4:
                    return 2;
                default:
                    throw new IllegalStateException("Illegal result from getDistinctColorCount()");
            }
        }

        /**
         * Creates a deep copx of this {@link DCCFrameBufferPalette}.
         *
         * @return The deep copx.
         */
        public DCCFrameBufferPalette copy()
        {
            DCCFrameBufferPalette copy = new DCCFrameBufferPalette();
            copy.setColor(0, getColor(0));
            copy.setColor(1, getColor(1));
            copy.setColor(2, getColor(2));
            copy.setColor(3, getColor(3));
            return copy;
        }

        /**
         * Finds the provided sample value in this palette and returns the index of the sample with that value.  If the
         * provided sample value is not in this palette, an {@link IllegalArgumentException} is thrown.
         *
         * @param sampleValue The sample value for which to search.
         * @return The index of the first sample with that value.
         * @throws IllegalArgumentException If the provided value does not appear in this palette.
         */
        public int findSampleValue(byte sampleValue)
        {
            if (paletteColors[0] == sampleValue) return 0;
            if (paletteColors[1] == sampleValue) return 1;
            if (paletteColors[2] == sampleValue) return 2;
            if (paletteColors[3] == sampleValue) return 3;
            throw new IllegalArgumentException(
                    "Sample value " + (sampleValue & 0xFF) + " does not appear in the palette [" +
                    (paletteColors[0] & 0xFF) + ", " +
                    (paletteColors[1] & 0xFF) + ", " +
                    (paletteColors[2] & 0xFF) + ", " +
                    (paletteColors[3] & 0xFF) + "]");
        }

        /**
         * Sorts the samples in this palette.  The sort order will be the opposite of a natural {@link Integer} sorting
         * with the exception that the provided value will be counted as greatest (transparent).
         *
         * @param transparentIndex The transparent index to shuffle to the end of the sample array.
         */
        public void sortSamples(byte transparentIndex)
        {
            // This is a very small array.  An unrolled bubble sort will be more efficient than anything else.
            int swap;
            int a = paletteColors[0] & 0xFF;
            int b = paletteColors[1] & 0xFF;
            int c = paletteColors[2] & 0xFF;
            int d = paletteColors[3] & 0xFF;
            if ((a == transparentIndex) || ((b != transparentIndex) && (a < b)))
            {
                swap = a;
                a = b;
                b = swap;
            }
            if ((b == transparentIndex) || ((c != transparentIndex) && (b < c)))
            {
                swap = b;
                b = c;
                c = swap;
            }
            if ((c == transparentIndex) || ((d != transparentIndex) && (c < d)))
            {
                swap = c;
                c = d;
                d = swap;
            }
            if ((a == transparentIndex) || ((b != transparentIndex) && (a < b)))
            {
                swap = a;
                a = b;
                b = swap;
            }
            if ((b == transparentIndex) || ((c != transparentIndex) && (b < c)))
            {
                swap = b;
                b = c;
                c = swap;
            }
            if ((a == transparentIndex) || ((b != transparentIndex) && (a < b)))
            {
                swap = a;
                a = b;
                b = swap;
            }
            paletteColors[0] = (byte) a;
            paletteColors[1] = (byte) b;
            paletteColors[2] = (byte) c;
            paletteColors[3] = (byte) d;
        }


        /**
         * Creates an inverted palette values array.  This array excludes any transparent values and, bearing that in
         * mind, is returned in reverse order.  For example, if this palette contained the samples [A,B,C,D] and D were
         * a transparent value, this method would return an array [C,B,A].
         *
         * @param transparentIndex The index to treat as transparent.
         * @return The inverted palette values array.
         */
        public byte[] getInvertedPaletteValuesArray(int transparentIndex)
        {
            byte t = (byte) (transparentIndex);
            if (paletteColors[0] == t) return Utilities.EMPTY_BYTE_ARRAY;
            if (paletteColors[1] == t) return new byte[]{paletteColors[0]};
            if (paletteColors[2] == t) return new byte[]{paletteColors[1], paletteColors[0]};
            if (paletteColors[3] == t)
            {
                return new byte[]{paletteColors[2], paletteColors[1], paletteColors[0]};
            }
            return new byte[]{paletteColors[3], paletteColors[2], paletteColors[1], paletteColors[0]};
        }

        /**
         * Rearranges this palette in such a way that it is as similar as possible to the provided palette.  For
         * example, if this palette contained the samples [A,B,C,0] (where <code>0</code> is transparent) and the
         * provided palette contained the samples [E,A,F,B], this palette would be rearranged to be [B,A,C,0].  Note
         * that the transparent values are still located at the end of the palette; this method will not rearrange the
         * samples in such a way that the transparent samples appear before the opaque samples.
         *
         * @param palette          The other palette.
         * @param transparentIndex The index of the transparent color in this palette.
         */
        public void rearrangeToResemble(DCCFrameBufferPalette palette, int transparentIndex)
        {
            byte t = (byte) (transparentIndex);
            int maxloop = 3;
            while ((maxloop > 0) && (this.getColor(maxloop) == t))
            {
                maxloop--;
            }
            while ((maxloop > 0) && (palette.getColor(maxloop) == t))
            {
                maxloop--;
            }
            maxloop++;

            for (int i = 0; i < maxloop; i++)
            {
                for (int j = 0; j < maxloop; j++)
                {
                    if ((palette.getColor(j) == paletteColors[i]) && (i != j))
                    {
                        byte temp = paletteColors[i];
                        paletteColors[i] = paletteColors[j];
                        paletteColors[j] = temp;
                    }
                }
            }
        }

        /**
         * Retrieves the pixel mask necessary when translating from the provided palette to this palette.
         *
         * @param palette The other palette.
         * @return The pixel mask to convert from that palette to this one.
         */
        public int getPixelMask(DCCFrameBufferPalette palette)
        {
            if (palette.getColor(3) == this.getColor(3))
            {
                if (palette.getColor(2) == this.getColor(2))
                {
                    if (palette.getColor(1) == this.getColor(1))
                    {
                        if (palette.getColor(0) == this.getColor(0))
                        {
                            return 0x0;
                        } else
                        {
                            return 0x1;
                        }
                    } else
                    {
                        if (palette.getColor(0) == this.getColor(0))
                        {
                            return 0x2;
                        } else
                        {
                            return 0x3;
                        }
                    }
                } else
                {
                    if (palette.getColor(1) == this.getColor(1))
                    {
                        if (palette.getColor(0) == this.getColor(0))
                        {
                            return 0x4;
                        } else
                        {
                            return 0x5;
                        }
                    } else
                    {
                        if (palette.getColor(0) == this.getColor(0))
                        {
                            return 0x6;
                        } else
                        {
                            return 0x7;
                        }
                    }
                }
            } else
            {
                if (palette.getColor(2) == this.getColor(2))
                {
                    if (palette.getColor(1) == this.getColor(1))
                    {
                        if (palette.getColor(0) == this.getColor(0))
                        {
                            return 0x8;
                        } else
                        {
                            return 0x9;
                        }
                    } else
                    {
                        if (palette.getColor(0) == this.getColor(0))
                        {
                            return 0xA;
                        } else
                        {
                            return 0xB;
                        }
                    }
                } else
                {
                    if (palette.getColor(1) == this.getColor(1))
                    {
                        if (palette.getColor(0) == this.getColor(0))
                        {
                            return 0xC;
                        } else
                        {
                            return 0xD;
                        }
                    } else
                    {
                        if (palette.getColor(0) == this.getColor(0))
                        {
                            return 0xE;
                        } else
                        {
                            return 0xF;
                        }
                    }
                }
            }
        }

        /**
         * Creates a {@link String} to describe this object.
         *
         * @return A {@link String} to describe this object.
         */
        public String toString()
        {
            return "[" + (paletteColors[0] & 0xFF) + ", " + (paletteColors[1] & 0xFF) + ", " +
                   (paletteColors[2] & 0xFF) + ", " + (paletteColors[3] & 0xFF) + "]";
        }
    }

    /**
     * This class is designed to represent a single frame buffer cell in DCC processing.
     *
     * @author Zachary Palmer
     */
    static class DCCFrameBufferCell
    {
        /**
         * The width of this cell.
         */
        protected int width;
        /**
         * The height of this cell.
         */
        protected int height;
        /**
         * The X offset (in pixels from the natural position of this cell) of this cell.
         */
        protected int xOffset;
        /**
         * The Y offset (in pixels from the natural position of this cell) of this cell.
         */
        protected int yOffset;

        /**
         * General constructor.
         */
        public DCCFrameBufferCell()
        {
            width = 4;
            height = 4;
            xOffset = 0;
            yOffset = 0;
        }

        public int getHeight()
        {
            return height;
        }

        public void setHeight(int height)
        {
            this.height = height;
        }

        public int getWidth()
        {
            return width;
        }

        public void setWidth(int width)
        {
            this.width = width;
        }

        public int getXOffset()
        {
            return xOffset;
        }

        public void setXOffset(int offset)
        {
            xOffset = offset;
        }

        public int getYOffset()
        {
            return yOffset;
        }

        public void setYOffset(int offset)
        {
            yOffset = offset;
        }
    }

    /**
     * This class is a data container for DCC frame headers.
     *
     * @author Zachary Palmer
     */
    static class DCCFrameHeader
    {
        /**
         * The width of the DCC frame.
         */
        protected int width;
        /**
         * The height of the DCC frame.
         */
        protected int height;
        /**
         * The X offset of the DCC frame.
         */
        protected int xOffset;
        /**
         * The Y offset of the DCC frame.
         */
        protected int yOffset;
        /**
         * The optional data size for the DCC frame.
         */
        protected int optionalDataSize;
        /**
         * The size of the DCC frame in coded bytes.
         */
        protected int codedBytes;
        /**
         * Whether or not the DCC frame is coded bottom-up.
         */
        protected boolean bottomUp;
        /**
         * The optional data for this DCC frame.
         */
        protected byte[] optionalData;

        public DCCFrameHeader(int width, int height, int xOffset, int yOffset, int optionalDataSize,
                              int codedBytes, boolean bottomUp)
        {
            this.bottomUp = bottomUp;
            this.codedBytes = codedBytes;
            this.height = height;
            this.optionalDataSize = optionalDataSize;
            this.width = width;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }

        public boolean isBottomUp()
        {
            return bottomUp;
        }

        public int getCodedBytes()
        {
            return codedBytes;
        }

        public int getHeight()
        {
            return height;
        }

        public byte[] getOptionalData()
        {
            return optionalData;
        }

        public int getOptionalDataSize()
        {
            return optionalDataSize;
        }

        public int getWidth()
        {
            return width;
        }

        public int getXOffset()
        {
            return xOffset;
        }

        public int getYOffset()
        {
            return yOffset;
        }

        public void setOptionalData(byte[] optionalData)
        {
            this.optionalData = optionalData;
        }

        public void setYOffset(int offset)
        {
            yOffset = offset;
        }

        /**
         * Generates a string describing some of the information contained in this header.
         */
        public String toString()
        {
            return "DCC Frame Header: " + width + "x" + height + " @ (" + xOffset + "," + yOffset + "): " +
                   (bottomUp ? "Bottom-Up" : "Top-Down");
        }
    }

// STATIC METHODS ////////////////////////////////////////////////////////////////

}

// END OF FILE