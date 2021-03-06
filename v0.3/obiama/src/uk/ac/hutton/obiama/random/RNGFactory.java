/*
 * uk.ac.hutton.obiama.random: RNGFactory.java
 * 
 * Copyright (C) 2013 The James Hutton Institute
 * 
 * This file is part of obiama-0.3.
 * 
 * obiama-0.3 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * obiama-0.3 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with obiama-0.3. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contact information: Gary Polhill, The James Hutton Institute, Craigiebuckler,
 * Aberdeen. AB15 8QH. UK. gary.polhill@hutton.ac.uk
 */
package uk.ac.hutton.obiama.random;

import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import cern.jet.random.engine.RandomSeedGenerator;

import uk.ac.hutton.obiama.exception.ErrorHandler;
import uk.ac.hutton.obiama.model.Log;
import uk.ac.hutton.obiama.msb.ObiamaSetUp;
import uk.ac.hutton.util.Bug;
import uk.ac.hutton.util.Panic;

/**
 * <!-- RNGFactory -->
 * 
 * This class provides builders for various RNG classes based on options
 * supplied on the OBIAMA command-line. The simplest method to call is
 * {@link #getRNG()}, which will just return whichever RNG the user requested on
 * the command-line parameterised according to their specifications. Otherwise,
 * methods are provided for each class in the package. Utilities are also
 * provided to compute seeds.
 * 
 * @author Gary Polhill
 */
public class RNGFactory {
  /**
   * The name of the default RNG class to use
   */
  public static String DEFAULT_RNG_CLASS = "MTRNG";

  /**
   * Global RNG
   */
  private static RNG global = null;

  /**
   * Constructor (disabled)
   */
  private RNGFactory() {
    // do nothing
  }

  /**
   * <!-- getRNG -->
   * 
   * Get the global random number generator
   * 
   * @return An RNG configured as per the command-line arguments
   */
  public static RNG getRNG() {
    if(global == null) global = getNewRNG();
    return global;
  }

  /**
   * <!-- getNewRNG -->
   * 
   * Get a new user-configured RNG from the information supplied on the command
   * line. It just looks for a method in this class with the same name as the
   * class taking parameters as arguments.
   * 
   * @return An RNG configured as per the command-line arguments
   */
  public static RNG getNewRNG() {
    return getNewRNG(ObiamaSetUp.getRNGClassName(), ObiamaSetUp.getRNGParams());
  }

  /**
   * <!-- getNewRNG -->
   * 
   * Get an RNG configured as per caller's specifications.
   * 
   * @param rngClass Name of RNG class to return
   * @param paramStr String to parse (with
   *          {@link #parseRNGParams(java.lang.String)})
   * @return The RNG
   */
  public static RNG getNewRNG(String rngClass, String paramStr) {
    return getNewRNG(rngClass, paramStr == null ? null : parseRNGParams(paramStr));
  }

  /**
   * <!-- getNewRNG -->
   * 
   * Get an RNG configured as per caller's specifications.
   * 
   * @param rngClass Name of RNG class to return
   * @param params Map of parameters for the RNG class
   * @return The RNG
   */
  public static RNG getNewRNG(String rngClass, Map<String, String> params) {
    if(rngClass == null) rngClass = DEFAULT_RNG_CLASS;
    if(params == null) params = new HashMap<String, String>();
    try {
      Method method = RNGFactory.class.getDeclaredMethod(rngClass, Map.class);
      Log.rng(rngClass, params);
      return (RNG)method.invoke(new RNGFactory(), params);
    }
    catch(SecurityException e) {
      ErrorHandler.fatal(e, "building random number generator");
      throw new Panic();
    }
    catch(NoSuchMethodException e) {
      ErrorHandler.redo(new ClassNotFoundException(rngClass), "building random number generator");
      throw new Panic();
    }
    catch(IllegalArgumentException e) {
      throw new Bug();
    }
    catch(IllegalAccessException e) {
      throw new Bug();
    }
    catch(InvocationTargetException e) {
      throw new Bug();
    }
  }

  /**
   * <!-- DeviceReaderRNG -->
   * 
   * <p>
   * Build a {@link DeviceReaderRNG} using the parameter map supplied as
   * arguments. This class recognises the following parameters:
   * </p>
   * 
   * <ul>
   * <li><i>device</i> (required): File name of device to get random numbers
   * from.</li>
   * <li><i>save</i>: File to save values retrieved to for reuse later.</li>
   * <li><i>discard</i>: Discard this many bytes generated by the device before
   * using the rest.</li>
   * <li><i>retry</i>: Number of times to retry if for some reason the device
   * cannot supply numbers temporarily.</li>
   * <li><i>wait</i> (must be supplied if <i>retry</i> is): Number of
   * milliseconds to wait between retries.</li>
   * </ul>
   * 
   * <p>
   * Other parameters will be ignored.
   * </p>
   * 
   * @param params
   * @return A DeviceReaderRNG
   */
  public static RNG DeviceReaderRNG(Map<String, String> params) {
    if(!params.containsKey("device")) {
      ErrorHandler.redo(new Exception("DeviceReaderRNG requires the device parameter to be specified"),
          "building DeviceReaderRNG");
    }
    if(params.containsKey("save")) {
      try {
        if(params.containsKey("discard") && !params.containsKey("retry")) {
          try {
            return new DeviceReaderRNG(params.get("device"), params.get("save"),
                Integer.parseInt(params.get("discard")));
          }
          catch(NumberFormatException e) {
            ErrorHandler.redo(e, "initialising DeviceReaderRNG with discard = \"" + params.get("discard")
              + "\"; this parameter needs to be a parseable integer");
            throw new Panic();
          }
        }
        else if(params.containsKey("retry") && params.containsKey("wait") && !params.containsKey("discard")) {
          try {
            return new DeviceReaderRNG(params.get("device"), params.get("save"), Integer.parseInt(params.get("retry")),
                Integer.parseInt(params.get("wait")));
          }
          catch(NumberFormatException e) {
            ErrorHandler.redo(e, "initialising DeviceReaderRNG with retry = \"" + params.get("retry")
              + "\" and wait = \"" + params.get("wait") + "; both of which need to be parseable integers");
            throw new Panic();
          }
        }
        else if(params.containsKey("discard") && params.containsKey("retry") && params.containsKey("wait")) {
          try {
            return new DeviceReaderRNG(params.get("device"), params.get("save"),
                Integer.parseInt(params.get("discard")), Integer.parseInt(params.get("retry")), Integer.parseInt(params
                    .get("wait")));
          }
          catch(NumberFormatException e) {
            ErrorHandler.redo(e, "initialising DeviceReaderRNG with discard = \"" + params.get("discard")
              + "\", retry = \"" + params.get("retry") + "\" and wait = \"" + params.get("wait")
              + "; all of which need to be parseable integers");
            throw new Panic();
          }
        }
        else {
          return new DeviceReaderRNG(params.get("device"), params.get("save"));
        }
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "building DeviceReaderRNG with save = \"" + params.get("save") + "\" and device = \""
          + params.get("device") + "\", either of which might have caused the exception");
        throw new Panic();
      }
    }
    else {
      try {
        if(params.containsKey("discard") && !params.containsKey("retry")) {
          try {
            return new DeviceReaderRNG(params.get("device"), Integer.parseInt(params.get("discard")));
          }
          catch(NumberFormatException e) {
            ErrorHandler.redo(e, "initialising DeviceReaderRNG with discard = \"" + params.get("discard")
              + "\"; this parameter needs to be a parseable integer");
            throw new Panic();
          }
        }
        else if(params.containsKey("retry") && params.containsKey("wait") && !params.containsKey("discard")) {
          try {
            return new DeviceReaderRNG(params.get("device"), Integer.parseInt(params.get("retry")),
                Integer.parseInt(params.get("wait")));
          }
          catch(NumberFormatException e) {
            ErrorHandler.redo(e, "initialising DeviceReaderRNG with retry = \"" + params.get("retry")
              + "\" and wait = \"" + params.get("wait") + "; both of which need to be parseable integers");
            throw new Panic();
          }
        }
        else if(params.containsKey("discard") && params.containsKey("retry") && params.containsKey("wait")) {
          try {
            return new DeviceReaderRNG(params.get("device"), Integer.parseInt(params.get("discard")),
                Integer.parseInt(params.get("retry")), Integer.parseInt(params.get("wait")));
          }
          catch(NumberFormatException e) {
            ErrorHandler.redo(e, "initialising DeviceReaderRNG with discard = \"" + params.get("discard")
              + "\", retry = \"" + params.get("retry") + "\" and wait = \"" + params.get("wait")
              + "; all of which need to be parseable integers");
            throw new Panic();
          }
        }
        else {
          return new DeviceReaderRNG(params.get("device"));
        }
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "building DeviceReaderRNG with device = \"" + params.get("device") + "\"");
        throw new Panic();
      }
    }
  }

  /**
   * <!-- DevRandomRNG -->
   * 
   * <p>
   * Build a {@link DevRandomRNG} using the parameter map supplied as arguments.
   * This class recognises the following parameters:
   * </p>
   * 
   * <ul>
   * <li><i>save</i>: File to save values retrieved to for reuse later.</li>
   * <li><i>retry</i>: Number of times to retry if for some reason the device
   * cannot supply numbers temporarily.</li>
   * <li><i>wait</i> (must be supplied if <i>retry</i> is): Number of
   * milliseconds to wait between retries.</li>
   * </ul>
   * 
   * <p>
   * Other parameters will be ignored.
   * </p>
   * 
   * @param params
   * @return A DevRandomRNG
   */
  public static RNG DevRandomRNG(Map<String, String> params) {
    if(params.containsKey("save") && !params.containsKey("retry")) {
      try {
        return new DevRandomRNG(params.get("save"));
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "building DevRandomRNG with save = \"" + params.get("save") + "\" on device "
          + DevRandomRNG.DEVICE + ", either of which might have caused the exception");
        throw new Panic();
      }
    }
    else if(params.containsKey("retry") && params.containsKey("wait") && !params.containsKey("save")) {
      try {
        return new DevRandomRNG(Integer.parseInt(params.get("retry")), Integer.parseInt(params.get("wait")));
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "building DevRandomRNG on device " + DevRandomRNG.DEVICE);
        throw new Panic();
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(e,
            "building DevRandomRNG with retry = \"" + params.get("retry") + "\" and wait =\"" + params.get("wait")
              + "\"; both of which must be parseable integers");
        throw new Panic();
      }
    }
    else if(params.containsKey("save") && params.containsKey("retry") && params.containsKey("wait")) {
      try {
        return new DevRandomRNG(params.get("save"), Integer.parseInt(params.get("retry")), Integer.parseInt(params
            .get("wait")));
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "building DevRandomRNG with save = \"" + params.get("save") + "\" on device "
          + DevRandomRNG.DEVICE + ", either of which might have caused the exception");
        throw new Panic();
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(e,
            "building DevRandomRNG with retry = \"" + params.get("retry") + "\" and wait =\"" + params.get("wait")
              + "\"; both of which must be parseable integers");
        throw new Panic();
      }
    }
    else {
      try {
        return new DevRandomRNG();
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "building DevRandomRNG on device " + DevRandomRNG.DEVICE);
        throw new Panic();
      }
    }
  }

  /**
   * <!-- DevURandomRNG -->
   * 
   * <p>
   * Build a {@link DevURandomRNG} using the parameter map supplied as
   * arguments. This class recognises the following parameters:
   * </p>
   * 
   * <ul>
   * <li><i>save</i>: File to save values retrieved to for reuse later.</li>
   * <li><i>retry</i>: Number of times to retry if for some reason the device
   * cannot supply numbers temporarily.</li>
   * <li><i>wait</i> (must be supplied if <i>retry</i> is): Number of
   * milliseconds to wait between retries.</li>
   * </ul>
   * 
   * <p>
   * Other parameters will be ignored.
   * </p>
   * 
   * @param params
   * @return A DevURandomRNG
   */
  public static RNG DevURandomRNG(Map<String, String> params) {
    if(params.containsKey("save") && !params.containsKey("retry")) {
      try {
        return new DevURandomRNG(params.get("save"));
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "building DevURandomRNG with save = \"" + params.get("save") + "\" on device "
          + DevURandomRNG.DEVICE + ", either of which might have caused the exception");
        throw new Panic();
      }
    }
    else if(params.containsKey("retry") && params.containsKey("wait") && !params.containsKey("save")) {
      try {
        return new DevURandomRNG(Integer.parseInt(params.get("retry")), Integer.parseInt(params.get("wait")));
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "building DevURandomRNG on device " + DevURandomRNG.DEVICE);
        throw new Panic();
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(e, "building DevURandomRNG with retry = \"" + params.get("retry") + "\" and wait =\""
          + params.get("wait") + "\"; both of which must be parseable integers");
        throw new Panic();
      }
    }
    else if(params.containsKey("save") && params.containsKey("retry") && params.containsKey("wait")) {
      try {
        return new DevURandomRNG(params.get("save"), Integer.parseInt(params.get("retry")), Integer.parseInt(params
            .get("wait")));
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "building DevURandomRNG with save = \"" + params.get("save") + "\" on device "
          + DevURandomRNG.DEVICE + ", either of which might have caused the exception");
        throw new Panic();
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(e, "building DevURandomRNG with retry = \"" + params.get("retry") + "\" and wait =\""
          + params.get("wait") + "\"; both of which must be parseable integers");
        throw new Panic();
      }
    }
    else {
      try {
        return new DevURandomRNG();
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "building DevURandomRNG on device " + DevURandomRNG.DEVICE);
        throw new Panic();
      }
    }
  }

  /**
   * <!-- DRandRNG -->
   * 
   * <p>
   * Build a {@link DRandRNG} using the parameter map supplied as arguments.
   * This class recognises the following parameters:
   * </p>
   * 
   * <ul>
   * <li><i>discard</i>: Discard this many bytes generated by the device before
   * using the rest.</li>
   * <li><i>seed</i>: Seed.</li>
   * <li><i>table</i>: If the seed is not supplied, you can instead look up a
   * seed in Colt's {@link RandomSeedGenerator} table. The value for this
   * parameter is two integers separated by a colon. (e.g. 123:456789).
   * </ul>
   * 
   * <p>
   * Other parameters will be ignored.
   * </p>
   * 
   * @param params
   * @return A DRandRNG
   */
  public static RNG DRandRNG(Map<String, String> params) {
    long seed = getIntSeed(params);
    Log.seed(seed);
    if(params.containsKey("discard")) {
      try {
        return new DRandRNG(seed, Integer.parseInt(params.get("discard")));
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(e, "initialising DRandRNG with discard = \"" + params.get("discard")
          + "\"; this parameter needs to be a parseable integer");
        throw new Panic();
      }
    }
    return new DRandRNG(seed);
  }

  /**
   * <!-- FileRNG -->
   * 
   * <p>
   * Build a {@link FileRNG} using the parameter map supplied as arguments. This
   * class recognises the following parameters:
   * </p>
   * 
   * <ul>
   * <li><i>file</i> (required): File name to get random numbers from.</li>
   * <li><i>discard</i>: Discard this many bytes generated by the device before
   * using the rest.</li>
   * <li><i>retry</i>: Number of times to retry if for some reason the device
   * cannot supply numbers temporarily.</li>
   * <li><i>wait</i> (must be supplied if <i>retry</i> is): Number of
   * milliseconds to wait between retries.</li>
   * </ul>
   * 
   * <p>
   * Other parameters will be ignored.
   * </p>
   * 
   * @param params
   * @return A FileRNG
   */
  public static RNG FileRNG(Map<String, String> params) {
    if(!params.containsKey("file")) {
      ErrorHandler.redo(new Exception("FileRNG requires the file parameter to be specified"), "building FileRNG");
    }
    try {

      if(params.containsKey("discard") && !params.containsKey("retry")) {
        try {
          return new FileRNG(params.get("file"), Integer.parseInt(params.get("discard")));
        }
        catch(NumberFormatException e) {
          ErrorHandler.redo(e, "initialising FileRNG with discard = \"" + params.get("discard")
            + "\"; this parameter needs to be a parseable integer");
          throw new Panic();
        }
      }
      else if(params.containsKey("retry") && params.containsKey("wait") && !params.containsKey("discard")) {
        try {
          return new FileRNG(params.get("file"), Integer.parseInt(params.get("retry")), Integer.parseInt(params
              .get("wait")));
        }
        catch(NumberFormatException e) {
          ErrorHandler.redo(e,
              "initialising FileRNG with retry = \"" + params.get("retry") + "\", wait = \"" + params.get("wait")
                + "\"; these parameters all need to be parseable integers");
          throw new Panic();
        }

      }
      else if(params.containsKey("discard") && params.containsKey("retry") && params.containsKey("wait")) {
        try {
          return new FileRNG(params.get("file"), Integer.parseInt(params.get("discard")), Integer.parseInt(params
              .get("retry")), Integer.parseInt(params.get("wait")));
        }
        catch(NumberFormatException e) {
          ErrorHandler.redo(e, "initialising FileRNG with discard = \"" + params.get("discard") + "\", retry = \""
            + params.get("retry") + "\", wait = \"" + params.get("wait")
            + "\"; these parameters all need to be parseable integers");
          throw new Panic();
        }
      }
      else {
        return new FileRNG(params.get("file"));
      }
    }
    catch(FileNotFoundException e) {
      ErrorHandler.redo(e, "initialising FileRNG from file " + params.get("file"));
      throw new Panic();
    }
  }

  /**
   * <!-- JavaRNG -->
   * 
   * <p>
   * Build a {@link JavaRNG} using the parameter map supplied as arguments. This
   * class recognises the following parameters:
   * </p>
   * 
   * <ul>
   * <li><i>discard</i>: Discard this many bytes generated by the device before
   * using the rest.</li>
   * <li><i>seed</i>: Seed.</li>
   * <li><i>table</i>: If the seed is not supplied, you can instead look up a
   * seed in Colt's {@link RandomSeedGenerator} table. The value for this
   * parameter is two integers separated by a colon. (e.g. 123:456789).
   * </ul>
   * 
   * <p>
   * Other parameters will be ignored.
   * </p>
   * 
   * @param params
   * @return A JavaRNG
   */
  public static RNG JavaRNG(Map<String, String> params) {
    long seed = getLongSeed(params);
    Log.seed(seed);
    if(params.containsKey("discard")) {
      try {
        return new JavaRNG(seed, Integer.parseInt(params.get("discard")));
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(e, "initialising JavaRNG with discard = \"" + params.get("discard")
          + "\"; this parameter needs to be a parseable integer");
        throw new Panic();
      }
    }
    return new JavaRNG(seed);
  }

  /**
   * <!-- JavaSecureRNG -->
   * 
   * <p>
   * Build a {@link JavaSecureRNG} using the parameter map supplied as
   * arguments. This class recognises the following parameters:
   * </p>
   * 
   * <ul>
   * <li><i>discard</i>: Discard this many bytes generated by the device before
   * using the rest.</li>
   * <li><i>seed</i>: Seed.</li>
   * <li><i>table</i>: If the seed is not supplied, you can instead look up a
   * seed in Colt's {@link RandomSeedGenerator} table. The value for this
   * parameter is two integers separated by a colon. (e.g. 123:456789).
   * </ul>
   * 
   * <p>
   * Other parameters will be ignored.
   * </p>
   * 
   * @param params
   * @return A JavaSecureRNG
   */
  public static RNG JavaSecureRNG(Map<String, String> params) {
    long seed = getLongSeed(params);
    Log.seed(seed);
    if(params.containsKey("discard")) {
      try {
        return new JavaSecureRNG(seed, Integer.parseInt(params.get("discard")));
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(e, "initialising JavaSecureRNG with discard = \"" + params.get("discard")
          + "\"; this parameter needs to be a parseable integer");
        throw new Panic();
      }
    }
    return new JavaSecureRNG(seed);
  }

  /**
   * <!-- MTRNG -->
   * 
   * <p>
   * Build a {@link MTRNG} using the parameter map supplied as arguments. This
   * class recognises the following parameters:
   * </p>
   * 
   * <ul>
   * <li><i>discard</i>: Discard this many bytes generated by the device before
   * using the rest.</li>
   * <li><i>seed</i>: Seed.</li>
   * <li><i>table</i>: If the seed is not supplied, you can instead look up a
   * seed in Colt's {@link RandomSeedGenerator} table. The value for this
   * parameter is two integers separated by a colon. (e.g. 123:456789).
   * </ul>
   * 
   * <p>
   * Other parameters will be ignored.
   * </p>
   * 
   * @param params
   * @return An MTRNG
   */
  public static RNG MTRNG(Map<String, String> params) {
    long seed = getIntSeed(params);
    Log.seed(seed);
    if(params.containsKey("discard")) {
      try {
        return new MTRNG(seed, Integer.parseInt(params.get("discard")));
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(e, "initialising MTRNG with discard = \"" + params.get("discard")
          + "\"; this parameter needs to be a parseable integer");
        throw new Panic();
      }
    }
    return new MTRNG(seed);
  }

  /**
   * <!-- RandomOrgRNG -->
   * 
   * <p>
   * Build a {@link RandomOrgRNG} using the parameter map supplied as arguments.
   * This class recognises the following parameters:
   * </p>
   * 
   * <ul>
   * <li><i>save</i>: File to save values retrieved to for reuse later.</li>
   * <li><i>chunk</i>: Number of bytes to request from <a
   * href="http://www.random.org/">random.org</a> at a time</li>
   * </ul>
   * 
   * <p>
   * Other parameters will be ignored.
   * </p>
   * 
   * @param params
   * @return A RandomOrgRNG
   */
  public static RNG RandomOrgRNG(Map<String, String> params) {
    if(params.containsKey("save") && !params.containsKey("chunk")) {
      try {
        return new RandomOrgRNG(params.get("save"));
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "initialising RandomOrgRNG with save = \"" + params.get("save") + "\"");
        throw new Panic();
      }
    }
    else if(params.containsKey("chunk") && !params.containsKey("save")) {
      try {
        return new RandomOrgRNG(Integer.parseInt(params.get("chunk")));
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(e, "initialising RandomOrgRNG with chunk = \"" + params.get("chunk")
          + "\"; this parameter needs to be a parseable integer");
        throw new Panic();
      }
    }
    else if(params.containsKey("chunk") && params.containsKey("save")) {
      try {
        return new RandomOrgRNG(Integer.parseInt(params.get("chunk")), params.get("save"));
      }
      catch(FileNotFoundException e) {
        ErrorHandler.redo(e, "initialising RandomOrgRNG with save = \"" + params.get("save") + "\"");
        throw new Panic();
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(e, "initialising RandomOrgRNG with chunk = \"" + params.get("chunk")
          + "\"; this parameter needs to be a parseable integer");
        throw new Panic();
      }
    }
    else {
      return new RandomOrgRNG();
    }
  }

  /**
   * <!-- getLongSeed -->
   * 
   * If the user hasn't specified a seed, then they can use the table parameter
   * to specify a point in a seed table to use. Otherwise, a seed is generated
   * from the current time.
   * 
   * @return A <code>long</code> seed generated according to command-line
   *         specifications.
   */
  public static long getLongSeed() {
    Long seed = ObiamaSetUp.getRNGSeed();
    Map<String, String> params = ObiamaSetUp.getRNGParams();
    if(seed == null) {
      if(params.containsKey("table")) {
        String[] rowCol = params.get("table").split(":");
        if(rowCol.length != 2) {
          ErrorHandler.redo(new Exception("Invalid argument to table RNG parameter \"" + params.get("table")
            + "\": expecting <row>:<col>"), "generating seed");
        }
        try {
          seed = createLongTableSeed(Integer.parseInt(rowCol[0]), Integer.parseInt(rowCol[1]));
        }
        catch(NumberFormatException e) {
          ErrorHandler.redo(new Exception("Invalid argument to table RNG parameter \"" + params.get("table")
            + "\": values either side of the : must be parseable integers"), "generating seed");
          throw new Panic();
        }
      }
      else {
        seed = createLongSeed();
      }
    }
    return seed;
  }

  /**
   * <!-- getLongSeed -->
   * 
   * @param params
   * @return The seed extracted from the parameters, or the value returned by
   *         {@link #getLongSeed()}
   */
  private static long getLongSeed(Map<String, String> params) {
    if(params != null && params.containsKey("seed")) {
      try {
        return Long.parseLong(params.get("seed"));
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(new Exception("Invalid argument to \"seed\" parameter: value \"" + params.get("seed")
          + "\" must be a parseable long"), "generating seed");
        throw new Panic();
      }
    }
    else {
      return getLongSeed();
    }
  }

  /**
   * <!-- getIntSeed -->
   * 
   * See {@link #getLongSeed}.
   * 
   * @return A seed suitable for casting to <code>int</code> according to user
   *         specifications
   */
  public static long getIntSeed() {
    Long seed = ObiamaSetUp.getRNGSeed();
    Map<String, String> params = ObiamaSetUp.getRNGParams();
    if(seed == null) {
      if(params.containsKey("table")) {
        String[] rowCol = params.get("table").split(":");
        if(rowCol.length != 2) {
          ErrorHandler.redo(new Exception("Invalid argument to table RNG parameter \"" + params.get("table")
            + "\": expecting <row>:<col>"), "generating seed");
        }
        try {
          seed = createIntTableSeed(Integer.parseInt(rowCol[0]), Integer.parseInt(rowCol[1]));
        }
        catch(NumberFormatException e) {
          ErrorHandler.redo(new Exception("Invalid argument to table RNG parameter \"" + params.get("table")
            + "\": values either side of the : must be parseable integers"), "generating seed");
          throw new Panic();
        }
      }
      else {
        seed = createIntSeed();
      }
    }
    return seed;
  }

  /**
   * <!-- getIntSeed -->
   * 
   * @param params
   * @return A seed extracted from <code>params</code>, or the value returned by
   *         {@link getIntSeed()}
   */
  private static long getIntSeed(Map<String, String> params) {
    if(params != null && params.containsKey("seed")) {
      try {
        return Long.parseLong(params.get("seed"));
      }
      catch(NumberFormatException e) {
        ErrorHandler.redo(new Exception("Invalid argument to \"seed\" parameter: value \"" + params.get("seed")
          + "\" must be a parseable long"), "generating seed");
        throw new Panic();
      }
    }
    else {
      return getIntSeed();
    }
  }

  /**
   * <!-- createLongSeed -->
   * 
   * @return A <code>long</code> seed based on the current time
   */
  public static long createLongSeed() {
    return System.currentTimeMillis();
  }

  /**
   * <!-- createIntSeed -->
   * 
   * @return A seed suitable for casting to <code>int</code> based on the
   *         current time (this does assume 32 bit integers...)
   */
  public static long createIntSeed() {
    return (System.currentTimeMillis() / 100L) & 0xFFFFFFFFL;
  }

  /**
   * <!-- createLongTableSeed -->
   * 
   * Create a seed using Colt's {@link RandomSeedGenerator} to retrieve a seed
   * from a table. To make a <code>long</code> seed, this method retrieves two
   * seeds from the table.
   * 
   * @param row Row in the table to use (can be any non-negative
   *          <code>int</code>)
   * @param col Column in the table to use (can be any non-negative
   *          <code>int</code>)
   * @return A seed
   */
  public static long createLongTableSeed(int row, int col) {
    RandomSeedGenerator seedGen = new RandomSeedGenerator(row, col);
    long seed = seedGen.nextSeed();
    seed <<= Integer.SIZE;
    seed |= (long)seedGen.nextSeed();
    return seed;
  }

  /**
   * <!-- createIntTableSeed -->
   * 
   * 
   * Create a seed using Colt's {@link RandomSeedGenerator} to retrieve a seed
   * from a table. The {@link RandomSeedGenerator#nextSeed()} method returns an
   * integer by default.
   * 
   * @param row Row in the table to use (can be any non-negative
   *          <code>int</code>)
   * @param col Column in the table to use (can be any non-negative
   *          <code>int</code>)
   * @return A seed
   */
  public static long createIntTableSeed(int row, int col) {
    RandomSeedGenerator seedGen = new RandomSeedGenerator(row, col);
    return seedGen.nextSeed();
  }

  /**
   * <!-- parseRNGParams -->
   * 
   * Parse a string containing comma-separated parameter=value pairs into a
   * {@link java.util.Map}. If a (non-negative) integer is given instead of a
   * parameter=value pair, this is assumed to be the seed.
   * 
   * @param paramStr
   * @return The map of parameter = value associations.
   */
  public static Map<String, String> parseRNGParams(String paramStr) {
    Map<String, String> params = new HashMap<String, String>();
    String[] paramEqValue = paramStr.split(",");
    for(String pair: paramEqValue) {
      if(pair.matches("^\\d+$")) {
        params.put("seed", pair);
      }
      else {
        String[] paramValue = pair.split("=");
        if(paramValue.length != 2) {
          ErrorHandler.redo(new Exception("Expecting <RNG parameter>=<value> pair, got \"" + pair + "\""),
              "processing RNG parameter string: " + paramStr);
        }
        params.put(paramValue[0], paramValue[1]);
      }
    }
    return params;
  }
}
