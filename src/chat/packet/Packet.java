package chat.packet;

import java.io.Serializable;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.lang.StringBuilder;
import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;

/*
 * Author: Murray Heymann 
 * 2016
 *
 * Packet is a data structure for sending data over a network.
 *
 * HAMILTON
 * Where are you taking me?
 *
 * ANGELICA
 * I'm about to change your life.
 *
 * HAMILTON
 * Then by all means, lead the way.
 * --> Helpless, Hamilton
 */

public class Packet implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public int code;
	public String name;
    public String data;
	public String to;
	public Set<String> users;

    
	public Packet() {
		this.code = -1;
		this.name = null;
		this.data = null;
		this.to = null;
		this.users = null;
	}
    public Packet(int code, String name, String data, String to) {
		this.code = code;
		this.name = name;
		this.data = null;
		this.to = null;
		this.users = null;

		switch (code) {
			case Code.QUIT:
			case Code.GET_ULIST:
				break;
			case Code.SEND:
			case Code.BROADCAST:
				this.to = to;
			case Code.ECHO:
			case Code.LOGIN:
				this.data = data;
				break;
			default:
				System.err.printf("Invalid code upon creating packet.\n");
				break;
		}

    }

	public String toString() {
		StringBuilder sb = new StringBuilder();	

		sb.append("packet:\n");
		sb.append("\tCode: " + this.code + "\n");
		sb.append("\tName: " + this.name + "\n");
		sb.append("\tData: " + this.data + "\n");
		sb.append("\tTo: " + this.to + "\n");
		sb.append("\tUsers: " + this.users + "\n");
		sb.append("packet end\n");

		return sb.toString();
	}
    
	public void setUserList(Set<String> users) {
		this.users = users;
	}

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

	private static String bytesToHex(byte[] hash) {
		return DatatypeConverter.printHexBinary(hash);
	}


	private static byte[] getSha256(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(data);
			return hash;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void sendPacket(Packet packet, SocketChannel socketChannel) throws IOException 
	{
		int size;
		ByteBuffer buffer = null;
		ByteBuffer sizeBuffer = null;
		ByteBuffer hashBuffer = null;
		ByteBuffer[] srcs = new ByteBuffer[3];
		byte[] byteArray = null;
		byte[] hash = null;
		
		byteArray = Serializer.serialize(packet);

		sizeBuffer = ByteBuffer.allocate(4);

		size = byteArray.length;
		sizeBuffer.putInt(size);

		hash = getSha256(byteArray);
		hashBuffer = ByteBuffer.wrap(hash);

		buffer = ByteBuffer.wrap(byteArray);

		System.out.printf("Send hash %s\n", bytesToHex(hash));
		sizeBuffer.flip();
		srcs[0] = sizeBuffer;
		srcs[1] = hashBuffer;
		srcs[2] = buffer;
		socketChannel.write(srcs);
	}

	public static Packet receivePacket(SocketChannel socketChannel) throws ClassNotFoundException, IOException {
		final int intSize = 4;
		int r = -1;
		int packetSize = -1;
		ByteBuffer buffer = null;
		byte[] byteArr = null;
		byte[] hash = null;
		Packet packet = null;
		
		
		buffer = ByteBuffer.allocate(intSize);
		buffer.clear();
		r = -1;
		try {
			r = socketChannel.read(buffer);
		} catch (IOException e) {
			System.err.printf("io exceoption, user probably went offline in a rogue fassion\n");
			e.printStackTrace();
		}
		if (r == 0) {
			/*  
			 * this is currently handled as user going offline.  is this still
			 * valid?
			 * */

			System.err.printf("This shouldn't happen!!!!!\n");
			System.err.printf("the buffer is empty for some reason.  \n");
			System.err.printf("****************************************************** \n");
			return null;
		}
		if (r == -1) {
			socketChannel.close();
			socketChannel = null;
			return null;
		}

		packetSize = buffer.getInt(0);
		/*
		System.out.printf("%d bytes\n", packetSize);
		*/
		if (packetSize <= 0) {
			/*  
			 * this is currently handled as user going offline.  is this still
			 * valid?
			 * */
			return null;
		}


		buffer = null;
		buffer = ByteBuffer.allocate(32);

		int cumSize = 0;
		do {
			r = -1;
			r = socketChannel.read(buffer);
			if (r == -1) {
				socketChannel.close();
				socketChannel = null;
				return null;
			}
			cumSize += r;
		} while (cumSize < 32);
		hash = buffer.array();


		buffer = null;
		buffer = ByteBuffer.allocate(packetSize);

		cumSize = 0;
		do {
			r = -1;
			r = socketChannel.read(buffer);
			if (r == -1) {
				socketChannel.close();
				socketChannel = null;
				return null;
			}
			cumSize += r;
		} while (cumSize < packetSize);
		byteArr = buffer.array();

		String hashString = bytesToHex(hash);
		if (hashString.compareTo(bytesToHex(getSha256(byteArr))) != 0) {
			System.out.printf("hash doesn't check out\n");
		} else {
			System.out.printf("Recv hash %s\n", hashString);
		}

        packet = (Packet)Serializer.deserialize(byteArr);
		return packet;
	}
}
