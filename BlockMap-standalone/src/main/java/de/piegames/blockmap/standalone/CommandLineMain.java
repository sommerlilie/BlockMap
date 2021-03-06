package de.piegames.blockmap.standalone;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import com.flowpowered.nbt.regionfile.RegionFile;

import de.piegames.blockmap.Region;
import de.piegames.blockmap.RegionFolder;
import de.piegames.blockmap.color.BiomeColorMap;
import de.piegames.blockmap.color.BlockColorMap;
import de.piegames.blockmap.color.BlockColorMap.InternalColorMap;
import de.piegames.blockmap.renderer.RegionRenderer;
import de.piegames.blockmap.renderer.RegionShader.DefaultShader;
import de.piegames.blockmap.renderer.RenderSettings;
import de.piegames.blockmap.standalone.CommandLineMain.CommandRender;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.RunLast;

@Command(name = "blockmap",
		version = { "1.1.1" },
		subcommands = { CommandRender.class, HelpCommand.class })
public class CommandLineMain implements Runnable {

	private static Log	log	= LogFactory.getLog(RegionRenderer.class);

	@Option(names = { "-V", "--version" },
			versionHelp = true,
			description = "Print version information and exit.")
	boolean				versionRequested;

	@Option(names = { "--verbose", "-v" }, description = "Be chatty")
	boolean				verbose;

	@Command(name = "render", sortOptions = false)
	public static class CommandRender implements Runnable {

		@ParentCommand
		private CommandLineMain		main;

		@Option(names = { "--output", "-o" },
				description = "The location of the output images. Must not be a file. Non-existant folders will be created.",
				paramLabel = "<FOLDER>",
				defaultValue = "./",
				showDefaultValue = Visibility.ALWAYS)
		private Path				output;
		@Parameters(index = "0",
				paramLabel = "INPUT",
				description = "Path to the world data. Normally, this should point to a 'region/' of a world.")
		private Path				input;
		@Option(names = { "-c", "--color-map" },
				paramLabel = "<DEFAULT|CAVES|NO_FOLIAGE|OCEAN_GROUND>",
				description = "Load a built-in color map.",
				defaultValue = "DEFAULT")
		private InternalColorMap	colorMap;
		@Option(names = { "-s", "--shader" },
				paramLabel = "<FLAT|RELIEF|BIOMES|HEIGHTMAP>",
				description = "The height shading to use in post processing.",
				showDefaultValue = Visibility.ALWAYS,
				defaultValue = "RELIEF")
		private DefaultShader		shader;
		@Option(names = "--custom-color-map", description = "Load a custom color map from the specified file. Overrides --color-map.")
		private Path				customColorMap;
		@Option(names = "--custom-biome-map", description = "Load a custom biome color map from the specified file.")
		private Path				customBiomeMap;

		@Option(names = { "--min-Y", "--min-height" }, description = "Don't draw blocks lower than this height.", defaultValue = "0")
		private int					minY;
		@Option(names = { "--max-Y", "--max-height" }, description = "Don't draw blocks higher than this height.", defaultValue = "255")
		private int					maxY;
		@Option(names = "--min-X", description = "Don't draw blocks to the east of this coordinate.", defaultValue = "-2147483648")
		private int					minX;
		@Option(names = "--max-X", description = "Don't draw blocks to the west of this coordinate.", defaultValue = "2147483647")
		private int					maxX;
		@Option(names = "--min-Z", description = "Don't draw blocks to the north of this coordinate.", defaultValue = "-2147483648")
		private int					minZ;
		@Option(names = "--max-Z", description = "Don't draw blocks to the south of this coordinate.", defaultValue = "2147483647")
		private int					maxZ;
		@Option(names = { "-l", "--lazy" },
				description = "Don't render region files if there is already an up to date. This saves time when rendering the same world regularly with the same settings.")
		private boolean				lazy;

		@Option(names = "--create-tile-html",
				description = "Generate a tiles.html in the output directory that will show all rendered images ona mapin your browsed.")
		private boolean				createHtml;
		@Option(names = "--create-big-image",
				description = "Merge all rendered images into a single file. May require a lot of RAM.")
		private boolean				createBigPic;

		@Override
		public void run() {
			if (main.verbose) {
				Configurator.setRootLevel(Level.DEBUG);
			}
			RenderSettings settings = new RenderSettings();
			settings.minX = minX;
			settings.maxX = maxX;
			settings.minY = minY;
			settings.maxY = maxY;
			settings.minZ = minZ;
			settings.maxZ = maxZ;
			if (customColorMap == null)
				settings.blockColors = colorMap.getColorMap();
			else
				try (Reader r = Files.newBufferedReader(customColorMap)) {
					settings.blockColors = BlockColorMap.load(r);
				} catch (IOException e) {
					log.error("Could not load custom block color map", e);
					return;
				}
			if (customBiomeMap == null)
				settings.biomeColors = BiomeColorMap.loadDefault();
			else
				try (Reader r = Files.newBufferedReader(customBiomeMap)) {
					settings.biomeColors = BiomeColorMap.load(r);
				} catch (IOException e) {
					log.error("Could not load custom block color map", e);
					return;
				}
			settings.shader = shader.getShader();

			RegionRenderer renderer = new RegionRenderer(settings);
			log.debug("Input " + input.toAbsolutePath());
			log.debug("Output: " + output.toAbsolutePath());
			RegionFolder world = RegionFolder.load(input);
			/* Statistics */
			int rendered = 0, skipped = 0, failed = 0;
			for (Region r : world.regions.values()) {
				try {
					r.renderedPath = output.resolve(r.path.getFileName().toString().replace(".mca", ".png"));
					if (lazy
							&& Files.exists(r.renderedPath)
							&& Files.getLastModifiedTime(r.renderedPath).compareTo(Files.getLastModifiedTime(r.path)) > 0) {
						skipped++;
						log.debug("Skipping file " + r.path.getFileName() + " because " + r.renderedPath + " is newer and we are lazy.");
						continue;
					}
					RegionFile rf = new RegionFile(r.path);
					BufferedImage b = renderer.render(r.position, rf);
					log.debug("Saving image to " + r.renderedPath.toAbsolutePath());
					ImageIO.write(b, "png", Files.newOutputStream(r.renderedPath));
					rendered++;
				} catch (IOException e) {
					log.error("Could not render region file", e);
					failed++;
				}
			}
			if (createBigPic)
				PostProcessing.createBigImage(world, output, settings);
			if (createHtml)
				PostProcessing.createTileHtml(world, output, settings);
			// log.info("Done. Rendered " + (rendered == 0 ? "no" : rendered) + " region files"
			// + (lazy ? (failed > 0 ? "," : " and") + " skipped " + (failed == 0 && rendered == 0 ? "all " : "") + (skipped == 0 ? "none" : skipped) :
			// "")
			// + (failed > 0 ? " and failed to render " + failed + " due to exceptions." : "."));
			log.info("Done. Region files rendered/skipped/failed/total: " + rendered + "/" + skipped + "/" + failed + "/" + (rendered + skipped + failed));
		}

	}

	@Override
	public void run() {
		if (verbose) {
			Configurator.setRootLevel(Level.DEBUG);
		}

		/*
		 * Using generics will make sure the class is only loaded now and not before. Loading this class may cause to load JavaFX classes which
		 * might not be on the class path with some java installations. This way, even users without JavaFX can still use the CLI
		 */
		try {
			Class.forName("de.piegames.blockmap.guistandalone.GuiMain").getMethod("main2").invoke(null);
		} catch (NoClassDefFoundError e) {
			log.fatal("Could not load GUI classes. Please make sure you have JavaFX loaded and on your class path. "
					+ "Alternatively, use Java 8 which includes JavaFX. You can use the BlockMap CLI anyway with `BlockMap help` or `BlockMap render`.",
					e);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException
				| ClassNotFoundException e) {
			log.fatal("Could not load GUI main class", e);
		}
	}

	public static void main(String... args) {
		CommandLine cli = new CommandLine(new CommandLineMain());
		cli.parseWithHandler(new RunLast(), args);
	}
}
