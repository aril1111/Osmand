package net.osmand.binary;

import gnu.trove.list.array.TIntArrayList;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import net.osmand.osm.MapUtils;

import com.google.protobuf.CodedInputStreamRAF;
import com.google.protobuf.WireFormat;

public class BinaryMapIndexReader {
	
	private final RandomAccessFile raf;
	private int version;
	private List<MapRoot> mapIndexes = new ArrayList<MapRoot>();
	private CodedInputStreamRAF codedIS;
	

	
	
	public BinaryMapIndexReader(final RandomAccessFile raf) throws IOException {
		this.raf = raf;
		codedIS = CodedInputStreamRAF.newInstance(raf, 256);
		init();
	}

	private void init() throws IOException {
		while(true){
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				return;
			case OsmandOdb.OsmAndStructure.VERSION_FIELD_NUMBER :
				version = codedIS.readUInt32();
				break;
			case OsmandOdb.OsmAndStructure.MAPINDEX_FIELD_NUMBER:
				int length = readInt();
				int filePointer = codedIS.getTotalBytesRead();
				int oldLimit = codedIS.pushLimit(length);
				readMapIndex();
				codedIS.popLimit(oldLimit);
				codedIS.seek(filePointer + length);
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}
	}
	
	private void skipUnknownField(int tag) throws IOException{
		int wireType = WireFormat.getTagWireType(tag);
		if(wireType == WireFormat.WIRETYPE_FIXED32_LENGTH_DELIMITED){
			int length = readInt();
			codedIS.skipRawBytes(length);
		} else {
			codedIS.skipField(tag);
		}
	}
	
	private void readMapIndex() throws IOException {
		while(true){
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				return;
			case OsmandOdb.OsmAndMapIndex.LEVELS_FIELD_NUMBER :
				int length = readInt();
				int filePointer = codedIS.getTotalBytesRead();
				int oldLimit = codedIS.pushLimit(length);
				MapRoot mapRoot = readMapLevel();
				mapIndexes.add(mapRoot);
				codedIS.popLimit(oldLimit);
				codedIS.seek(filePointer + length);
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}
	}

	private MapRoot readMapLevel() throws IOException {
		MapRoot root = new MapRoot();
		while(true){
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				return root;
			case OsmandOdb.MapRootLevel.BOTTOM_FIELD_NUMBER :
				root.bottom = codedIS.readInt32();
				break;
			case OsmandOdb.MapRootLevel.LEFT_FIELD_NUMBER :
				root.left = codedIS.readInt32();
				break;
			case OsmandOdb.MapRootLevel.RIGHT_FIELD_NUMBER :
				root.right = codedIS.readInt32();
				break;
			case OsmandOdb.MapRootLevel.TOP_FIELD_NUMBER :
				root.top = codedIS.readInt32();
				break;
			case OsmandOdb.MapRootLevel.MAXZOOM_FIELD_NUMBER :
				root.maxZoom = codedIS.readInt32();
				break;
			case OsmandOdb.MapRootLevel.MINZOOM_FIELD_NUMBER :
				root.minZoom = codedIS.readInt32();
				break;
			case OsmandOdb.MapRootLevel.ROOT_FIELD_NUMBER :
				MapTree r = new MapTree();
				// left, ... already initialized 
				r.length = readInt();
				r.filePointer = codedIS.getTotalBytesRead();
				int oldLimit = codedIS.pushLimit(r.length);
				readMapTreeBounds(r, root.left, root.right, root.top, root.bottom);
				root.trees.add(r);
				codedIS.popLimit(oldLimit);
				codedIS.seek(r.filePointer + r.length);
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}
		
	}
	
	private void readMapTreeBounds(MapTree tree, int aleft, int aright, int atop, int abottom) throws IOException {
		int init = 0;
		while(true){
			if(init == 0xf){
				return;
			}
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				return;
			case OsmandOdb.MapTree.BOTTOM_FIELD_NUMBER :
				tree.bottom = codedIS.readSInt32() + abottom;
				init |= 1;
				break;
			case OsmandOdb.MapTree.LEFT_FIELD_NUMBER :
				tree.left = codedIS.readSInt32() + aleft;
				init |= 2;
				break;
			case OsmandOdb.MapTree.RIGHT_FIELD_NUMBER :
				tree.right = codedIS.readSInt32() + aright;
				init |= 4;
				break;
			case OsmandOdb.MapTree.TOP_FIELD_NUMBER :
				tree.top = codedIS.readSInt32() + atop;
				init |= 8;
				break;
		
			default:
				skipUnknownField(t);
				break;
			}
		}
	}
	
	
	
	public List<BinaryMapDataObject> searchMapIndex(SearchRequest req) throws IOException {
		for (MapRoot index : mapIndexes) {
			if (index.minZoom <= req.zoom && index.maxZoom >= req.zoom) {
				if(index.right < req.left || index.left > req.right || index.top > req.bottom || index.bottom < req.top){
					continue;
				}
				for (MapTree tree : index.trees) {
					codedIS.seek(tree.filePointer);
					int oldLimit = codedIS.pushLimit(tree.length);
					searchMapTreeBounds(tree, index.left, index.right, index.top, index.bottom, req);
					codedIS.popLimit(oldLimit);
				}
			}
		}
		return req.getSearchResults();
	}
	
	private void searchMapTreeBounds(MapTree tree, int pleft, int pright, int ptop, int pbottom,
			SearchRequest req) throws IOException {
		int init = 0;
		int lastIndexResult = -1;;
		while(true){
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			if(init == 0xf){
				init = 0;
				// coordinates are init
				if(tree.right < req.left || tree.left > req.right || tree.top > req.bottom || tree.bottom < req.top){
					return;
				}
			}
			switch (tag) {
			case 0:
				return;
			case OsmandOdb.MapTree.BOTTOM_FIELD_NUMBER :
				tree.bottom = codedIS.readSInt32() + pbottom;
				init |= 1;
				break;
			case OsmandOdb.MapTree.LEFT_FIELD_NUMBER :
				tree.left = codedIS.readSInt32() + pleft;
				init |= 2;
				break;
			case OsmandOdb.MapTree.RIGHT_FIELD_NUMBER :
				tree.right = codedIS.readSInt32() + pright;
				init |= 4;
				break;
			case OsmandOdb.MapTree.TOP_FIELD_NUMBER :
				tree.top = codedIS.readSInt32() + ptop;
				init |= 8;
				break;
			case OsmandOdb.MapTree.LEAFS_FIELD_NUMBER :
				int length = codedIS.readRawVarint32();
				int oldLimit = codedIS.pushLimit(length);
				if(lastIndexResult == -1){
					lastIndexResult = req.searchResults.size();
				}
				BinaryMapDataObject mapObject = readMapDataObject(tree.left, tree.right, tree.top, tree.bottom, req);
				if(mapObject != null){
					req.searchResults.add(mapObject);
					
				}
				codedIS.popLimit(oldLimit);
				break;
			case OsmandOdb.MapTree.SUBTREES_FIELD_NUMBER :
				MapTree r = new MapTree();
				// left, ... already initialized 
				r.length = readInt();
				r.filePointer = codedIS.getTotalBytesRead();
				oldLimit = codedIS.pushLimit(r.length);
				searchMapTreeBounds(r, tree.left, tree.right, tree.top, tree.bottom, req);
				codedIS.popLimit(oldLimit);
				codedIS.seek(r.filePointer + r.length);
				if(lastIndexResult >= 0){
					throw new IllegalStateException();
				}
				break;
			case OsmandOdb.MapTree.BASEID_FIELD_NUMBER :
				tree.baseId = codedIS.readUInt64();

				if (lastIndexResult != -1) {
					for (int i = lastIndexResult; i < req.searchResults.size(); i++) {
						BinaryMapDataObject rs = req.searchResults.get(i);
						rs.id += tree.baseId;
						if (rs.restrictions != null) {
							for (int j = 0; j < rs.restrictions.length; j++) {
								rs.restrictions[j] += tree.baseId;
							}
						}
					}
				}
				break;
			case OsmandOdb.MapTree.STRINGTABLE_FIELD_NUMBER :
				length = codedIS.readRawVarint32();
				oldLimit = codedIS.pushLimit(length);
				tree.stringTable = readStringTable();
				codedIS.popLimit(oldLimit);

				if (lastIndexResult != -1) {
					for (int i = lastIndexResult; i < req.searchResults.size(); i++) {
						BinaryMapDataObject rs = req.searchResults.get(i);
						if (rs.stringId != -1) {
							rs.name = tree.stringTable.get(rs.stringId);
						}
					}
				}
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}
	}
	private BinaryMapDataObject readMapDataObject(int left, int right, int top, int bottom, SearchRequest req) throws IOException {
		int tag = WireFormat.getTagFieldNumber(codedIS.readTag());
		if(OsmandOdb.MapData.COORDINATES_FIELD_NUMBER != tag) {
			throw new IllegalArgumentException();
		}
		req.cacheCoordinates.clear();
		int size = codedIS.readRawVarint32();
		int old = codedIS.pushLimit(size);
		int px = left;
		int py = top;
		boolean contains = false;
		while(codedIS.getBytesUntilLimit() > 0){
			int x = codedIS.readSInt32() + px;
			int y = codedIS.readSInt32() + py;
			req.cacheCoordinates.add(x);
			req.cacheCoordinates.add(y);
			px = x;
			py = y;
			if(!contains && req.left <= x && req.right >= x && req.top <= y && req.bottom >= y){
				contains = true;
			}
		}
		codedIS.popLimit(old);
		if(!contains){
			codedIS.skipRawBytes(codedIS.getBytesUntilLimit());
			return null;
		}
		
		BinaryMapDataObject dataObject = new BinaryMapDataObject();		
		dataObject.coordinates = req.cacheCoordinates.toArray();
		
		while(true){
			int t = codedIS.readTag();
			tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				return dataObject;
			case OsmandOdb.MapData.TYPES_FIELD_NUMBER :
				int sizeL = codedIS.readRawVarint32();
				codedIS.skipRawBytes(sizeL);
				// TODO read types
				break;
			case OsmandOdb.MapData.RESTRICTIONS_FIELD_NUMBER :
				// TODO read restrictions 
				sizeL = codedIS.readRawVarint32();
				codedIS.skipRawBytes(sizeL);
				break;
			case OsmandOdb.MapData.HIGHWAYMETA_FIELD_NUMBER :
				dataObject.highwayAttributes = codedIS.readInt32();
				break;
			case OsmandOdb.MapData.ID_FIELD_NUMBER :
				dataObject.id = codedIS.readSInt64();
				break;
			case OsmandOdb.MapData.STRINGID_FIELD_NUMBER :
				dataObject.stringId = codedIS.readUInt32();
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}
		
	}

	private List<String> readStringTable() throws IOException{
		List<String> list = new ArrayList<String>();
		while(true){
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				return list;
			case OsmandOdb.StringTable.S_FIELD_NUMBER :
				list.add(codedIS.readString());
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}
	}
	

	public RandomAccessFile getRaf() {
		return raf;
	}
	
	public int readByte() throws IOException{
		byte b = codedIS.readRawByte();
		if(b < 0){
			return b + 256;
		} else {
			return b;
		}
	}
	
	public final int readInt() throws IOException {
		int ch1 = readByte();
		int ch2 = readByte();
		int ch3 = readByte();
		int ch4 = readByte();
		return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
	}
	
	
	public int getVersion() {
		return version;
	}
	
	private static class MapRoot {
		int minZoom = 0;
		int maxZoom = 0;
		int left = 0;
		int right = 0;
		int top = 0;
		int bottom = 0;
		
		List<MapTree> trees = new ArrayList<MapTree>();
	}
	
	private static class MapTree {
		int filePointer = 0;
		int length = 0;
		
		int left = 0;
		int right = 0;
		int top = 0;
		int bottom = 0;
		
		long baseId = 0;
		
		List<String> stringTable = null;
		List<MapTree> subTrees = null;
		
	}
	
	public static SearchRequest buildSearchRequest(int sleft, int sright, int stop, int sbottom, int zoom){
		SearchRequest request = new SearchRequest();
		request.left = sleft;
		request.right = sright;
		request.top = stop;
		request.bottom = sbottom;
		request.zoom = zoom;
		return request;
	}
	
	public static class SearchRequest {
		int left = 0;
		int right = 0;
		int top = 0;
		int bottom = 0;
		int zoom = 15;
		List<BinaryMapDataObject> searchResults = new ArrayList<BinaryMapDataObject>();
		TIntArrayList cacheCoordinates = new TIntArrayList();
		
		protected SearchRequest(){
		}
		
		
		public List<BinaryMapDataObject> getSearchResults() {
			return searchResults;
		}
	}
	
	public static void main(String[] args) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(new File("e:\\Information\\OSM maps\\osmand\\Minsk.map.pbf"), "r");
		BinaryMapIndexReader reader = new BinaryMapIndexReader(raf);
		System.out.println("VERSION " + reader.getVersion());
		int sleft = MapUtils.get31TileNumberX(27.578);
		int sright = MapUtils.get31TileNumberX(27.583);
		int stop = MapUtils.get31TileNumberY(53.916);
		int sbottom = MapUtils.get31TileNumberY(53.9138);
		System.out.println("SEARCH " + sleft + " " + sright + " " + stop + " " + sbottom);

		for (BinaryMapDataObject obj : reader.searchMapIndex(buildSearchRequest(sleft, sright, stop, sbottom, 18))) {
			if (obj.getName() != null) {
				System.out.println(" " + obj.getName());
			}
		}
	}
	
}
