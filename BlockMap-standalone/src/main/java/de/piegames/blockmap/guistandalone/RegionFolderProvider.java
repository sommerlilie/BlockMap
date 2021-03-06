package de.piegames.blockmap.guistandalone;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import de.piegames.blockmap.RegionFolder;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.util.StringConverter;

public interface RegionFolderProvider {

	public ReadOnlyObjectProperty<RegionFolder> folderProperty();

	public List<Node> getGUI();

	public static class RegionFolderProviderImpl implements RegionFolderProvider {

		protected Path									regionFolderpath;
		protected ReadOnlyObjectWrapper<RegionFolder>	folder	= new ReadOnlyObjectWrapper<>();
		protected List<Node>							gui		= new ArrayList<>();

		public RegionFolderProviderImpl(Path regionFolderpath) {
			this.regionFolderpath = regionFolderpath;
			folder.set(RegionFolder.load(regionFolderpath));
		}

		@Override
		public List<Node> getGUI() {
			return gui;
		}

		@Override
		public ReadOnlyObjectProperty<RegionFolder> folderProperty() {
			return folder.getReadOnlyProperty();
		}
	}

	public static class WorldRegionFolderProvider implements RegionFolderProvider {

		public static enum Dimension {

			OVERWORLD("Overworld", 0, "region"),
			NETHER("Nether", -1, "DIM-1", "region"),
			END("End", 1, "DIM1", "region");

			int			index;
			String		name;
			String[]	path;

			Dimension(String name, int index, String... path) {
				this.name = name;
				this.index = index;
				this.path = path;
			}

			public Path resolve(Path base) {
				for (String s : path)
					base = base.resolve(s);
				return base;
			}
		}

		protected Path									worldPath;
		protected List<Dimension>						available;
		protected ReadOnlyObjectWrapper<RegionFolder>	folder	= new ReadOnlyObjectWrapper<>();
		protected List<Node>							gui		= new ArrayList<>();

		public WorldRegionFolderProvider(Path worldPath) {
			this.worldPath = worldPath;
			available = new ArrayList<>(3);
			for (Dimension d : Dimension.values())
				if (Files.exists(d.resolve(worldPath)) && Files.isDirectory(d.resolve(worldPath)))
					available.add(d);
			if (available.isEmpty())
				throw new IllegalArgumentException("Not a vaild world folder");
			ChoiceBox<Dimension> dimensionBox = new ChoiceBox<Dimension>();
			dimensionBox.setItems(FXCollections.observableList(available));
			dimensionBox.valueProperty().addListener((observable, oldValue, newValue) -> {
				if (oldValue != newValue)
					folder.set(RegionFolder.load(newValue.resolve(worldPath)));
			});
			dimensionBox.setValue(available.get(0));
			dimensionBox.setConverter(new StringConverter<RegionFolderProvider.WorldRegionFolderProvider.Dimension>() {

				@Override
				public String toString(Dimension object) {
					return object.name;
				}

				@Override
				public Dimension fromString(String string) {
					return null;
				}
			});
			gui.add(new Label("Dimension:"));
			gui.add(dimensionBox);
		}

		@Override
		public List<Node> getGUI() {
			return gui;
		}

		@Override
		public ReadOnlyObjectProperty<RegionFolder> folderProperty() {
			return folder.getReadOnlyProperty();
		}
	}

	public static RegionFolderProvider byPath(Path path) {
		if (Files.isDirectory(path) && Files.exists(path.resolve("level.dat")))
			return new WorldRegionFolderProvider(path);
		else
			return new RegionFolderProviderImpl(path);
	}
}