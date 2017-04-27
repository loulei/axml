package com.example.axml;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

public class Parser {
	
	public static byte[] rawDatas;
	
	public static final byte[] HEAD = {0x03, 0x00, 0x08, 0x00};
	
	public static final byte[] TYPE_CHUNK_STRING = {0x01, 0x00, 0x1c, 0x00};
	
	public static final byte[] TYPE_CHUNK_RESOURCEID = {(byte) 0x80, 0x01, 0x08, 0x00};
	
	public static final byte[] TYPE_CHUNK_START_NS = {0x00, 0x01, 0x10, 0x00};
	
	public static final byte[] TYPE_CHUNK_END_NS = {0x01, 0x01, 0x10, 0x00};
	
	public static final byte[] TYPE_CHUNK_START_TAG = {0x02, 0x01, 0x10, 0x00};
	
	public static final byte[] TYPE_CHUNK_END_TAG = {0x03, 0x01, 0x10, 0x00};
	
	public static final byte[] TYPE_CHUNK_TEXT = {0x04, 0x01, 0x10, 0x00};
	
	public static String[] stringPool;
	

	public static void main(String[] args) {
		File file = new File("file/AndroidManifest.xml");
		try {
			FileInputStream fis = new FileInputStream(file);
			int len = 0;
			byte[] buffer = new byte[1024];
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			while((len = fis.read(buffer)) != -1){
				bos.write(buffer, 0, len);
			}
			bos.flush();
			rawDatas = bos.toByteArray();
			bos.close();
			fis.close();
			
			System.out.println("read finish, size:"+rawDatas.length);
			
			boolean isValid = false;
			
			isValid = parseHeader();
			System.out.println("valid head:"+isValid);
			
			isValid = parseSize();
			System.out.println("valid file size:"+isValid);
			
			int offset = parseStringChunk();
			offset = parseResourceIdChunk(offset);
			writeResult();
			parseXmlContentChunk(offset);
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean parseHeader(){
		byte[] data_head = new byte[4];
		System.arraycopy(rawDatas, 0, data_head, 0, 4);
		return Arrays.equals(data_head, HEAD);
	}
	
	public static boolean parseSize(){
		byte[] data_size = new byte[4];
		System.arraycopy(rawDatas, 4, data_size, 0, 4);
		return Utils.byteToInt(data_size) == rawDatas.length;
	}
	
	public static int parseStringChunk(){
		byte[] u4 = new byte[4];
		int offset = 8;
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset += 4;
		boolean isValid = Arrays.equals(u4, TYPE_CHUNK_STRING);
		System.out.println("string chunk head :" + isValid);
		if(!isValid)
			return -1;
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset += 4;
		int chunkSize = Utils.byteToInt(u4);
		System.out.println("string chunk size:"+chunkSize);
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset += 4;
		int stringCount = Utils.byteToInt(u4);
		System.out.println("string count:"+stringCount);
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset += 4;
		int styleCount = Utils.byteToInt(u4);
		System.out.println("style count:"+styleCount);
		
		offset+=4; //pass unknow 4 bytes
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset += 4;
		int stringPoolOffset = Utils.byteToInt(u4);
		System.out.println("string pool offset: 0x"+Integer.toHexString(stringPoolOffset));
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset += 4;
		int stylePoolOffset = Utils.byteToInt(u4);
		System.out.println("style pool offset: 0x"+Integer.toHexString(stylePoolOffset));
		
		int[] stringOffsets = new int[stringCount];
		stringPool = new String[stringCount];
		for(int i=0; i<stringCount; i++){
			System.arraycopy(rawDatas, offset, u4, 0, 4);
			offset += 4;
			stringOffsets[i] = Utils.byteToInt(u4);
//			System.out.println("string offset " + i + ": 0x" + Integer.toHexString(stringOffsets[i]));
		}
		
		for(int i=0; i<stringCount; i++){
			int contentOffset = stringOffsets[i] + 0x8 + stringPoolOffset;
			offset = parseStringContent(contentOffset, i);
		}
		
		return offset;
	}
	
	private static int parseStringContent(int offset, int index){
		byte[] u2 = new byte[2];
		System.arraycopy(rawDatas, offset, u2, 0, 2);
		short strLen = Utils.byteToShort(u2);
//		System.out.println("strlen:"+strLen);
		byte[] data_string = new byte[strLen * 2];
		System.arraycopy(rawDatas, offset+2, data_string, 0, data_string.length);
//		System.out.println(Utils.bytes2HexStr(data_string));
		StringBuilder builder = new StringBuilder();
		for(int i=0; i<data_string.length; i+=2){
			builder.append((char)data_string[i]);
		}
		System.out.println("index:"+index+" string content:"+builder.toString());
		stringPool[index] = builder.toString();
		return offset + 2 + data_string.length + 2; // add 0x0000 tow byte string end
	}
	
	public static int parseResourceIdChunk(int offset){
		byte[] u4 = new byte[4];
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4;
//		System.out.println(Utils.bytes2HexStr(u4));
		boolean isValid = Arrays.equals(u4, TYPE_CHUNK_RESOURCEID);
		System.out.println("resourceId chunk head :" + isValid);
		if(!isValid)
			return -1;
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4;
		int chunkSize = Utils.byteToInt(u4);
		System.out.println("resourceId chunk size:"+chunkSize);
		int chunkCount = (chunkSize - 8)/4;
		System.out.println("resourceId count:"+chunkCount);
		
		int[] chunkIds = new int[chunkCount];
		for(int i=0; i<chunkCount; i++){
			System.arraycopy(rawDatas, offset, u4, 0, 4);
			offset+=4;
			chunkIds[i] = Utils.byteToInt(u4);
			System.out.println("resourceId " + i + " : 0x" + Integer.toHexString(chunkIds[i]));
		}
		return offset;
	}
	

	public static PrintWriter writer;
	public static String xmlns;
	public static String prefix;
	public static boolean isFirstNode = true;
	
	public static void writeResult(){
		File file = new File("file/_AndroidManifest.xml");
		try {
			FileOutputStream fos = new FileOutputStream(file);
			writer = new PrintWriter(fos, true);
			writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static int parseXmlContentChunk(int offset){
		
		if(offset >= rawDatas.length){
			System.out.println("parse end");
			return offset;
		}
		byte[] header = new byte[4];
		System.arraycopy(rawDatas, offset, header, 0, 4);
		offset+=4;
		
		byte[] u4 = new byte[4];
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4;
		int chunkSize = Utils.byteToInt(u4);
		System.out.println("chunk size:"+chunkSize);
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4;
		int lineNumber = Utils.byteToInt(u4);
		System.out.println("line number:"+lineNumber);
		
		if(Arrays.equals(header, TYPE_CHUNK_START_NS)){
			System.out.println("---------------- TYPE_CHUNK_START_NS ----------------");
			parseNamespace(offset, true);
		}else if(Arrays.equals(header, TYPE_CHUNK_END_NS)){
			System.out.println("---------------- TYPE_CHUNK_END_NS ----------------");
			parseNamespace(offset, false);
		}else if(Arrays.equals(header, TYPE_CHUNK_START_TAG)){
			System.out.println("---------------- TYPE_CHUNK_START_TAG ----------------");
			parseStartTag(offset);
		}else if(Arrays.equals(header, TYPE_CHUNK_END_TAG)){
			System.out.println("---------------- TYPE_CHUNK_END_TAG ----------------");
			parseEndTag(offset);
		}else if(Arrays.equals(header, TYPE_CHUNK_TEXT)){
			System.out.println("---------------- TYPE_CHUNK_TEXT ----------------");
			parseTextTag(offset);
		}
		
		
		return parseXmlContentChunk(offset - 12 + chunkSize);
	}
	
	
	private static void parseNamespace(int offset,  boolean isStart){
		offset+=4; //pass 4 unknown bytes
		byte[] u4 = new byte[4];
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4;
		int prefixIndex = Utils.byteToInt(u4);
		System.out.println("prefix:"+stringPool[prefixIndex]);
		
		prefix = stringPool[prefixIndex];
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4;
		int uriIndex = Utils.byteToInt(u4);
		System.out.println("Uri:"+stringPool[uriIndex]);
		
		xmlns = "xmlns:"+stringPool[prefixIndex]+"=\""+stringPool[uriIndex]+"\"";
		if(!isStart){
			writer.close();
			isStart = false;
		}
	}
	
	private static void parseStartTag(int offset){
		offset+=4; //pass 4 unknown bytes
		byte[] u4 = new byte[4];
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4; //pass namespace uri
		int namespaceUriIndex = Utils.byteToInt(u4);
		if(namespaceUriIndex == -1){
			System.out.println("namespace uri :"+null);
		}else{
			System.out.println("namespace uri :"+stringPool[namespaceUriIndex]);
		}
		
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4;
		int nameIndex = Utils.byteToInt(u4);
		System.out.println("name :"+stringPool[nameIndex]);
		
		writer.println("<"+stringPool[nameIndex]);
		if(isFirstNode){
			writer.println(xmlns);
			isFirstNode = false;
		}
		
		offset+=4; //pass flags 0x00140014
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4;
		int attributeCount = Utils.byteToInt(u4);
		System.out.println("attribute count:"+attributeCount);
		
		offset+=4; //pass 4 bytes  class Attribute
		
		byte[] u20 = new byte[20];
		if(attributeCount > 0){
			System.out.println("    ---- attribute start -----\n");
			for(int i=0; i<attributeCount; i++){
				System.arraycopy(rawDatas, offset, u20, 0, 20);
				offset+=20;
				
				System.arraycopy(u20, 0, u4, 0, 4);
				int uriIndex = Utils.byteToInt(u4);
				if(uriIndex != -1){
					System.out.println("uri:"+stringPool[uriIndex]);
				}
				
				System.arraycopy(u20, 4, u4, 0, 4);
				int attrNameIndex = Utils.byteToInt(u4);
				System.out.println("attribute name:"+stringPool[attrNameIndex]);
				
				System.arraycopy(u20, 8, u4, 0, 4);
				int strIndex = Utils.byteToInt(u4);
				if(strIndex != -1){
					System.out.println("string:"+stringPool[strIndex]);
				}
				
				System.arraycopy(u20, 12, u4, 0, 4);
				int type = Utils.byteToInt(u4);
				type = type >> 24;
				System.out.println("type:"+TypeValue.TYPE.getName(type));
				
//				if(type != TypeValue.TYPE.TYPE_STRING.getId()){
					System.arraycopy(u20, 16, u4, 0, 4);
					int data = Utils.byteToInt(u4);
					System.out.println("data:"+data);
//				}
				
				StringBuffer buffer = new StringBuffer();
				if(uriIndex != -1){
					buffer.append(prefix).append(":");
				}
				
				buffer.append(stringPool[attrNameIndex]).append("=");
				if(type == TypeValue.TYPE.TYPE_STRING.getId()){
					buffer.append("\"").append(stringPool[strIndex]).append("\"");
				}else if(type == TypeValue.TYPE.TYPE_INT_HEX.getId()){
					buffer.append("\"").append("0x").append(Integer.toHexString(data)).append("\"");
				}else if(type == TypeValue.TYPE.TYPE_INT_DEC.getId() || type == TypeValue.TYPE.TYPE_FIRST_INT.getId()){
					buffer.append("\"").append(data).append("\"");
				}else if(type == TypeValue.TYPE.TYPE_INT_BOOLEAN.getId()){
					buffer.append("\"").append(data != 0).append("\"");
				}else if(type == TypeValue.TYPE.TYPE_FIRST_INT.getId()){
					buffer.append("\"").append(data).append("\"");
				}else if(type == TypeValue.TYPE.TYPE_REFERENCE.getId() || type == TypeValue.TYPE.TYPE_ATTRIBUTE.getId()){
					buffer.append("\"").append("@").append(Integer.toHexString(data)).append("\"");
				}else{
					buffer.append("\"").append(data).append("\"");
				}
				writer.println(buffer.toString());
			}
			writer.println(">");
			System.out.println("    ---- attribute end -----\n");
		}
		
	}
	
	private static void parseEndTag(int offset){
		offset+=4; //pass 4 unknown bytes
		byte[] u4 = new byte[4];
		
		offset+=4; //pass namespace uri
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4;
		int nameIndex = Utils.byteToInt(u4);
		System.out.println("name :"+stringPool[nameIndex]);
		writer.println("</"+stringPool[nameIndex]+">");
	}
	
	private static void parseTextTag(int offset){
		offset+=4; //pass 4 unknown bytes
		byte[] u4 = new byte[4];
		
		System.arraycopy(rawDatas, offset, u4, 0, 4);
		offset+=4;
		int nameIndex = Utils.byteToInt(u4);
		System.out.println("name :"+stringPool[nameIndex]);
	}
}
