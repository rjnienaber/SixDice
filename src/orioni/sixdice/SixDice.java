package orioni.sixdice;

import orioni.jz.awt.image.RestrictableIndexColorModel;
import orioni.jz.common.exception.ParseException;
import orioni.jz.io.files.FileExtensionFilter;
import orioni.jz.io.files.FileUtilities;
import orioni.jz.util.Pair;
import orioni.jz.util.programparameters.ProgramParameter;
import orioni.jz.util.programparameters.ProgramParameterInstance;
import orioni.jz.util.programparameters.ProgramParameterManager;
import orioni.jz.util.strings.BoundedIntegerInterpreter;
import orioni.jz.util.strings.ColorInterpreter;
import orioni.jz.util.strings.StringInterpreter;
import orioni.jz.util.strings.StringUtilities;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;

/**
 * This is the SixDice launching class.  It provides a launch point for the SixDice frame which is distinct from that
 * class specifically to allow SixDice to operate in a headless mode.
 *
 * @author Zachary Palmer
 */
public class SixDice
{
// STATIC FIELDS /////////////////////////////////////////////////////////////////

// CONSTANTS /////////////////////////////////////////////////////////////////////

    /**
     * The version string for this program.
     */
    public static final String VERSION_STRING = "0.63";

// NON-STATIC FIELDS /////////////////////////////////////////////////////////////

// CONSTRUCTORS //////////////////////////////////////////////////////////////////

    /**
     * Private constructor.
     */
    private SixDice()
    {
        super();
    }

// NON-STATIC METHODS ////////////////////////////////////////////////////////////

// STATIC METHODS ////////////////////////////////////////////////////////////////

    /**
     * This method is designed to provide help regarding command line parameters.
     *
     * @param strings Strings to display before the help.
     */
    public static void showHelpAndBail(String... strings)
    {
        for (String s : strings)
        {
            System.err.println(s);
        }
        if (strings.length > 0) System.err.println();

        //                  00000000001111111111222222222233333333334444444444555555555566666666667777777777
        //                  01234567890123456789012345678901234567890123456789012345678901234567890123456789
        System.err.println("SixDice v" + VERSION_STRING);
        System.err.println("Usage: java -jar SixDice.jar [options] <file> [file] [file] ...");
        System.err.println();
        System.err.println("(Options marked with a '*' must be set.)");
        System.err.println("Options:");
        System.err.println("    -h, --help                Displays this help screen.");
        System.err.println("    -i, --import              Specifies that the program should convert image");
        System.err.println("                              files to animation files.  Setting the format");
        System.err.println("                              acts as a file extension filter.  Either -i or -e");
        System.err.println("                              must be used.");
        System.err.println("    -e, --export              Specifies that the program should convert");
        System.err.println("                              animation files to image files.  Requires that");
        System.err.println("                              the format be set.  Either -i or -e must be used.");
        System.err.println("*   -p, --palette             Specifies the palette to be used by the");
        System.err.println("                              animation.  For a list of palettes, try \"-p ?\".");
        System.err.println("    -f, --format              Specifies the format to be used by the converter.");
        System.err.println("                              For a list of formats supported by your JRE, try");
        System.err.println("                              \"-f ?\".");
        System.err.println("    -r, --recursive           Specifies that any directories should be");
        System.err.println("                              processed recursively.  Default is not to process");
        System.err.println("                              recursively.");
        System.err.println("    -s, --separator-string    Specifies the separator string used to separate");
        System.err.println("                              direction and frame number from the filename in");
        System.err.println("                              multi-frame export image sets.  By default, this");
        System.err.println("                              is \"__\".");
        System.err.println("*   -c, --codec               Specifies the codec to be used.  For a list of ");
        System.err.println("                              animation codecs, try \"-c ?\".");
        System.err.println("    -v, --virtual-clear-color Specifies the color in image files to be set as ");
        System.err.println("                              transparent.  For exports, this is the color that");
        System.err.println("                              transparent pixels will become.");
        System.err.println("    -x, --transparent-index   The index in the codec which should be treated as");
        System.err.println("                              transparent, or -1 for no transparent index.  By");
        System.err.println("                              default, this value is zero.");
        System.exit(1);
    }

    /**
     * This method executes SixDice as a program.  In this mode, specifying command-line arguments allows the user to
     * manipulate animation files in batch operations.  Not specifying command-line arguments launches the SixDice GUI.
     *
     * @param args See the content written to {@link System#err} by {@link SixDice#showHelpAndBail(String...)} for more
     *             information.
     */
    public static void main(String[] args)
    {
        if (args.length == 0)
        {
            try
            {
                SixDiceFrame sd = new SixDiceFrame();
                sd.setLocationRelativeTo(null);
                sd.setVisible(true);
            } catch (HeadlessException e)
            {
                showHelpAndBail();
            }
        } else
        {
            // Establish parameter context
            ProgramParameterManager ppm = new ProgramParameterManager();
            // Add help parameter
            ppm.addParameter(new ProgramParameter(new String[]{"h", "?", "help"}, true));
            // Add export mode parameter
            ppm.addParameter(new ProgramParameter("e", "export", false));
            // Add import mode parameter
            ppm.addParameter(new ProgramParameter("i", "import", false));
            // Add recursive parameter
            ppm.addParameter(new ProgramParameter("r", "recursive", false));
            // Add virtual clear color parameter
            ppm.addParameter(
                    new ProgramParameter<Color>("v", "virtual-clear-color", false, ColorInterpreter.SINGLETON));
            // Add palette parameter
            ppm.addParameter(new ProgramParameter<String>("p", "palette", false, StringInterpreter.SINGLETON));
            // Add format parameter
            ppm.addParameter(new ProgramParameter<String>("f", "format", false, StringInterpreter.SINGLETON));
            // Add separator parameter
            ppm.addParameter(new ProgramParameter<String>("s", "separator-string", false, StringInterpreter.SINGLETON));
            // Add codec parameter
            ppm.addParameter(new ProgramParameter<String>("c", "codec", false, StringInterpreter.SINGLETON));
            // Add transparent index parameter
            ppm.addParameter(
                    new ProgramParameter<Integer>(
                            "x", "transparent-index", false,
                            new BoundedIntegerInterpreter(-1, 255)));

            // Parse parameters
            Pair<ProgramParameterInstance[], String[]> parsedPair = null;
            try
            {
                parsedPair = ppm.parse(args);
            } catch (ParseException e)
            {
                showHelpAndBail();
            }

            final int modeExport = 0;
            final int modeImport = 1;
            final int modeNone = 2;
            int mode = modeNone;

            Color clearColor = null;
            boolean recursive = false;
            String paletteString = null;
            String format = null;
            String separator = "__";
            AnimationCodec codec = null;
            int transparentIndex = 0;

            for (ProgramParameterInstance ppi : parsedPair.getFirst())
            {
                if (("e".equals(ppi.getString())) || ("i".equals(ppi.getString())))
                {
                    if (mode != modeNone)
                    {
                        showHelpAndBail("Only one operational mode (import or export) can be chosen.");
                    } else
                    {
                        if ("i".equals(ppi.getString()))
                        {
                            mode = modeImport;
                        } else
                        {
                            mode = modeExport;
                        }
                    }
                } else if ("r".equals(ppi.getString()))
                {
                    recursive = true;
                } else if ("v".equals(ppi.getString()))
                {
                    clearColor = (Color) (ppi.getSubparameters()[0]);
                } else if ("p".equals(ppi.getString()))
                {
                    paletteString = (String) (ppi.getSubparameters()[0]);
                } else if ("f".equals(ppi.getString()))
                {
                    format = (String) (ppi.getSubparameters()[0]);
                } else if ("s".equals(ppi.getString()))
                {
                    separator = (String) (ppi.getSubparameters()[0]);
                } else if ("c".equals(ppi.getString()))
                {
                    String s = (String) (ppi.getSubparameters()[0]);
                    if ("dc6".equalsIgnoreCase(s))
                    {
                        codec = new DC6Codec();
                    } else if ("dcc".equalsIgnoreCase(s))
                    {
                        codec = new DCCCodec();
                    } else
                    {
                        showHelpAndBail("Invalid codec (\"" + s + "\").\nCodec must be one of {dc6, dcc}.");
                    }
                } else if ("x".equals(ppi.getString()))
                {
                    transparentIndex = (Integer) (ppi.getSubparameters()[0]);
                }
            }

            if (paletteString == null)
            {
                showHelpAndBail("A palette must be specified.  Use -p.");
            }
            RestrictableIndexColorModel palette = Diablo2DefaultPalettes.PALETTE_MAP.get(paletteString);
            if (palette == null)
            {
                String[] keys = Diablo2DefaultPalettes.PALETTE_MAP.keySet().toArray(
                        StringUtilities.EMPTY_STRING_ARRAY);
                String[] s = new String[keys.length + 1];
                s[0] = "The palette \"" + palette + "\" does not exist.  It must be one of the following:";
                for (int i = 1; i < s.length; i++)
                {
                    s[i] = "        \"" + keys[i - 1] + "\"";
                }
                showHelpAndBail(s);
            }

            if ((mode == modeExport) && (format == null))
            {
                showHelpAndBail("When converting animations to images, an image format must be specified.");
            }
            if (codec == null)
            {
                showHelpAndBail("A codec must be specified.");
            } else
            {
                codec.setTransparentIndex(transparentIndex);
            }
            if (!ImageIO.getImageReadersByFormatName(format).hasNext())
            {
                showHelpAndBail(
                        "The format \"" + format + "\" is not valid.  Format must be one of the following:" +
                        "\n        " + StringUtilities.createDelimitedList(
                                "\n        ",
                                ImageIO.getReaderFormatNames()));
            }

            if (parsedPair.getSecond().length == 0)
            {
                showHelpAndBail("No files specified.");
            }

            boolean dir = false;

            SixDiceCore core = new SixDiceCore(codec);
            core.setVirtualTransparentColor(clearColor);
            core.setTransparentToVirtualOnSave(clearColor != null);

            for (String s : parsedPair.getSecond())
            {
                File f = new File(s);
                if (f.exists())
                {
                    if (f.isDirectory())
                    {
                        dir = true;
                        if (mode == modeExport)
                        {
                            core.batchConvert(
                                    f, recursive, codec, format, separator, palette, System.out, System.err, true);
                        } else
                        {
                            core.batchConvert(
                                    f, recursive, new FileExtensionFilter('.' + format), codec, separator,
                                    palette, System.out, System.err, true);
                        }
                    } else
                    {
                        if (mode == modeExport)
                        {
                            core.convertAnimationToImage(
                                    FileUtilities.replaceFileExtension(f, '.' + format), format, separator, palette,
                                    System.out, System.err);
                        } else
                        {
                            core.convertImageToAnimation(f, codec, separator, palette, System.out, System.err);
                        }
                    }
                } else
                {
                    System.err.println("File " + f + " does not exist.");
                }
            }

            if ((!dir) && (recursive))
            {
                System.err.println(
                        "Warning: recursive was specified but none of the files provided were directories.");
            }
        }
    }
}

// END OF FILE