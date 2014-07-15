package com.xunlei.cloud.extstorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import com.mzh.FileCidUtility;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

public class FileSearchManager {
	private static FileSearchManager _instance;

	public static enum Type {
		USEJNI, USEJAVA
	}

	public static FileSearchManager instance() {
		if (_instance == null) {
			_instance = new FileSearchManager();
		}
		return _instance;
	}

	private ArrayList<String> formats = new ArrayList<String>();
	private ArrayList<String> video_formats = new ArrayList<String>();
	private ArrayList<String> pic_formats = new ArrayList<String>();
	private Map<String, SoftReference<Bitmap>> bitmapCaches;

	public FileSearchManager() {
		video_formats.add("avi");
		video_formats.add("rmvb");
		video_formats.add("mp4");
		video_formats.add("wmv");

		// pic_formats.add("jpg");

		formats.addAll(video_formats);
		formats.addAll(pic_formats);

		bitmapCaches = new HashMap<String, SoftReference<Bitmap>>();
	}

	// java ����ʱ�ѳ����ļ���Ŀ;
	private int _fileNum = 0;

	// ��Ƶ�б�
	ArrayList<String> video_list;
	HashMap<String, Integer> _video_table;

	public String getFixedPath(String path){
		int st = root_dir.length();
		int ed = path.lastIndexOf("/");
		return  path.substring(st, ed);
	}
	// ����path��value��
	public void genPathTable() {
		final int MAX_LOOP = 1024;
		_video_table = new HashMap<String, Integer>();
		for (String path : video_list) {
			String tmp = getFixedPath(path);
			
			int loop = 0, st = 0, ed = 0;
			
			while (!tmp.equals("") && loop++ < MAX_LOOP) {
				// Log.d("mzh", "wihle:"+tmp);
				path = tmp;
				if (_video_table.containsKey(tmp)) {
					int v = _video_table.get(tmp) + 1;
					_video_table.put(tmp, v);
				} else {
					_video_table.put(tmp, 1);
				}
				st = 0;
				ed = path.lastIndexOf("/");
				tmp = path.substring(st, ed);
				// Log.d("mzh", "get table:" + tmp + " s:" + st + " e:" + ed);
			}
		}

		ArrayList<Map.Entry<String, Integer>> entryList = new ArrayList<Map.Entry<String, Integer>>(
				_video_table.entrySet());
		Collections.sort(entryList,
				new Comparator<Map.Entry<String, Integer>>() {
					@Override
					public int compare(final Map.Entry<String, Integer> arg0,
							final Map.Entry<String, Integer> arg1) {
						Map.Entry<String, Integer> entry1 = arg0;
						Map.Entry<String, Integer> entry2 = arg1;
						/*
						 * String str1 = entry1.getKey().replaceAll("[^/]", "");
						 * String str2 = entry2.getKey().replaceAll("[^/]", "");
						 * if(str1.length() > str2.length()){ return 1; } else
						 * if(str1.length() < str2.length()){ return -1; }
						 */
						return (entry1.getKey().compareToIgnoreCase(entry2
								.getKey()));
						// return 0;
					}

				});
		// print start.....
		/*
		 * Iterator<Map.Entry<String, Integer>> iterator = entryList.iterator();
		 * String str; while (iterator.hasNext()) { Map.Entry<String, Integer>
		 * entry = (Map.Entry<String, Integer>) iterator .next(); str =
		 * entry.getKey(); int v = entry.getValue(); Log.d("mzh", "maps:" + str
		 * + "  value:" + v); }
		 */
		// print end......
	}

	public boolean isVideo(String name) {
		String ext = name.substring(name.lastIndexOf(".") + 1, name.length())
				.toLowerCase(Locale.getDefault());

		if (formats.contains(ext)) {
			return true;
		}
		return false;
	}

	private void pickOutVideoPath() {
		video_list = new ArrayList<String>();
		for (String str : _arrayList) {
			if (isVideo(str)) {
				video_list.add(str.replace("//", "/"));
			}
		}

		Collections.sort(video_list, new Comparator<String>() {
			@Override
			public int compare(final String arg0, String arg1) {
				String str1 = arg0.replaceAll("[^/]", "");
				String str2 = arg1.replaceAll("[^/]", "");
				if (str1.length() > str2.length()) {
					return 1;
				} else if (str1.length() < str2.length()) {
					return -1;
				}
				return 0;
			}

		});

		/*
		 * for (String str : video_list) { Log.d("mzh", "sort:"+str); }
		 */

		genPathTable();
		genCidMap();
		genThumbnails();
	}

	private String save_dir = ".xunleitv";
	private String root_dir = Environment.getExternalStorageDirectory()
			.getPath();
	private String save_path = root_dir + File.separator + save_dir;

	public void setRootDir(String dir) {
		root_dir = dir;
	}

	private void genThumbnails() {
		new Thread(new Runnable() {
			@Override
			public void run() {

				File f = new File(save_path);
				if (!(f.exists() && f.isDirectory())) {
					f.mkdir();
				}
				for (String str : video_list) {
					createThumLocalCache(str);
				}
				// print msg
				/*
				 * for(File fe : f.listFiles()){ Log.i("mzh",
				 * "file:"+fe.getAbsolutePath()); }
				 */
			}

		}).start();
	}

	/*
	 * ʹ���ļ� cid, ��С, px��ʽ��������ͼ������
	 */
	public static final int THUMB_PX_150 = 150;
	private int thumb_size = THUMB_PX_150;
	private int quality = 100;

	private boolean createThumLocalCache(String video_path) {
		File f;
		FileOutputStream out;
		String cid = getFilePathCid(video_path);
		String thumb_path;
		if (!cid.equals("")) {
			f = new File(video_path);
			thumb_path = String.format("%s/%s_%d_%d", save_path, cid,
					f.length(), thumb_size);
			f = new File(thumb_path);
			if (!f.exists()) {
				Bitmap bitmap = getVideoThumbnail(video_path, thumb_size,
						thumb_size, MediaStore.Images.Thumbnails.MICRO_KIND);
				try {
					f.createNewFile();
					out = new FileOutputStream(f);
					if (bitmap
							.compress(Bitmap.CompressFormat.PNG, quality, out)) {
						out.flush();
						out.close();
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

				bitmapCaches.put(video_path, new SoftReference<Bitmap>(bitmap));

				Log.i("mzh", "th:" + thumb_path);
			}
		}
		return false;
	}

	/*
	 * ���ļ�·��ȡ������ͼ
	 */
	public Bitmap getVideoThumbFrom(String path) {
		Bitmap btm = null;
		// ���ڴ滺��
		if (bitmapCaches.containsKey(path)) {
			SoftReference<Bitmap> softBitmap = bitmapCaches.get(path);
			Bitmap bmp = softBitmap.get();
			if (bmp != null) {
				return bmp;
			}
		}
		// ���ļ�����
		File f = new File(path); // ��Ƶ�ļ�
		FileInputStream in; // ������
		String cid = getFilePathCid(path); // ��ȡ�ļ�cid
		String thumb_path; // ����ͼλ��
		if (!cid.equals("") && f.exists()) { // �������ļ�cid
			thumb_path = String.format("%s/%s_%d_%d", save_path, cid, f.length(), thumb_size);
			try {
				f = new File(thumb_path);
				in = new FileInputStream(f);
				btm = BitmapFactory.decodeStream(in);
				bitmapCaches.put(path, new SoftReference<Bitmap>(btm));
				return btm;
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		// ��ʱ����
		btm = getVideoThumbnail(path, thumb_size,	thumb_size, MediaStore.Images.Thumbnails.MICRO_KIND);
		bitmapCaches.put(path, new SoftReference<Bitmap>(btm));
		return btm;
	}

	/*
	 * ���� map �ļ�·��cid ��Ӧ �ļ�cid
	 */
	private HashMap<String, String> cid_map;

	private void genCidMap() {
		String cid, key;
		cid_map = new HashMap<String, String>();
		for (String str : video_list) {
			key = FileCidUtility.instance.get_data_block_cid(str.getBytes());
			cid = FileCidUtility.instance.get_file_cid(str);
			cid_map.put(key, cid);
		}
	}

	/*
	 * ���ļ�·�� ȡ�� �ļ�cid
	 */
	public String getFilePathCid(String path) {
		String ret = "", key = FileCidUtility.instance.get_data_block_cid(path
				.getBytes());
		if (cid_map.containsKey(key))
			ret = cid_map.get(key);
		return ret;
	}

	/**
	 * ��ȡ��Ƶ������ͼ ��ͨ��ThumbnailUtils������һ����Ƶ������ͼ��Ȼ��������ThumbnailUtils������ָ����С������ͼ��
	 * �����Ҫ������ͼ�Ŀ�͸߶�С��MICRO_KIND��������Ҫʹ��MICRO_KIND��Ϊkind��ֵ���������ʡ�ڴ档
	 * 
	 * @param videoPath
	 *            ��Ƶ��·��
	 * @param width
	 *            ָ�������Ƶ����ͼ�Ŀ��
	 * @param height
	 *            ָ�������Ƶ����ͼ�ĸ߶ȶ�
	 * @param kind
	 *            ����MediaStore.Images.Thumbnails���еĳ���MINI_KIND��MICRO_KIND��
	 *            ���У�MINI_KIND: 512 x 384��MICRO_KIND: 96 x 96
	 * @return ָ����С����Ƶ����ͼ
	 */
	private Bitmap getVideoThumbnail(String videoPath, int width, int height,
			int kind) {
		Bitmap bitmap = null;
		// ��ȡ��Ƶ������ͼ
		bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, kind);
		System.out.println("w" + bitmap.getWidth());
		System.out.println("h" + bitmap.getHeight());
		bitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height,
				ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
		return bitmap;
	}

	/**
	 * ����ָ����ͼ��·���ʹ�С����ȡ����ͼ �˷���������ô��� 1.
	 * ʹ�ý�С���ڴ�ռ䣬��һ�λ�ȡ��bitmapʵ����Ϊnull��ֻ��Ϊ�˶�ȡ��Ⱥ͸߶ȣ�
	 * �ڶ��ζ�ȡ��bitmap�Ǹ��ݱ���ѹ������ͼ�񣬵����ζ�ȡ��bitmap����Ҫ������ͼ�� 2.
	 * ����ͼ����ԭͼ������û�����죬����ʹ����2.2�汾���¹���ThumbnailUtils��ʹ ������������ɵ�ͼ�񲻻ᱻ���졣
	 * 
	 * @param imagePath
	 *            ͼ���·��
	 * @param width
	 *            ָ�����ͼ��Ŀ��
	 * @param height
	 *            ָ�����ͼ��ĸ߶�
	 * @return ���ɵ�����ͼ
	 */
	private Bitmap getImageThumbnail(String imagePath, int width, int height) {
		Bitmap bitmap = null;
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		// ��ȡ���ͼƬ�Ŀ�͸ߣ�ע��˴���bitmapΪnull
		bitmap = BitmapFactory.decodeFile(imagePath, options);
		options.inJustDecodeBounds = false; // ��Ϊ false
		// �������ű�
		int h = options.outHeight;
		int w = options.outWidth;
		int beWidth = w / width;
		int beHeight = h / height;
		int be = 1;
		if (beWidth < beHeight) {
			be = beWidth;
		} else {
			be = beHeight;
		}
		if (be <= 0) {
			be = 1;
		}
		options.inSampleSize = be;
		// ���¶���ͼƬ����ȡ���ź��bitmap��ע�����Ҫ��options.inJustDecodeBounds ��Ϊ false
		bitmap = BitmapFactory.decodeFile(imagePath, options);
		// ����ThumbnailUtils����������ͼ������Ҫָ��Ҫ�����ĸ�Bitmap����
		bitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height,
				ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
		return bitmap;
	}

	public String traceFoundPaths() {
		String paths = "";
		for (String str : video_list) {
			paths += str + "\n";
			// Log.d("mzh", str);
		}
		return paths;
	}

	public boolean checkPath(String path) {
		return _video_table.containsKey(path);
	}

	private static ArrayList<String> _arrayList;

	public float checkFilesFrom(String path) {
		return checkFilesFrom(path, Type.USEJNI);
	}

	public float checkFilesFrom(String path, Type t) {
		_arrayList = new ArrayList<String>();
		float ret = System.currentTimeMillis();
		if (null == t) {
			t = Type.USEJNI;
		}
		switch (t) {
		case USEJNI: {
			ret = searchFiles(path);
			break;
		}
		case USEJAVA: {
			_fileNum = 0;
			getAllFiles(new File(path));
			ret = System.currentTimeMillis() - ret;
			break;
		}
		default:
			break;
		}
		pickOutVideoPath();
		return ret;
	}

	private void getAllFiles(File directory) {
		File files[] = directory.listFiles();

		if (files != null) {
			for (File f : files) {
				_fileNum++;
				addToList(f.getAbsolutePath());
				if (f.isDirectory()) {
					getAllFiles(f);
				} /*
				 * else { // Log.d("---", f.getAbsolutePath()); }
				 */
			}
		}
	}
	
	public int getVideosNum(String path){
		int ret = 0;
		String key = getFixedPath(path);
		if(_video_table.containsKey(key))
			ret = _video_table.get(key);
		return ret;
	}

	public int getFileNum() {
		return _fileNum;
	}

	// c call
	private void addToList(String p) {
		_arrayList.add(p);
	}

	// c
	private native float searchFiles(String path);

	static {
		System.loadLibrary("filesearch");
	}
}
