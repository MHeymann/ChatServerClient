// $Id$
package server;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReadWriteLock;
import packet.*;

public class ServerListener implements Runnable
{
	private int ports[];
	private ByteBuffer echoBuffer = ByteBuffer.allocate( 1024 );
	private boolean runningStatus = true;
	private ReadWriteLock statusLock = null;
	private Users users = null;

	public ServerListener(int ports[], Users users) {
		this.ports = ports;
		this.runningStatus = true;
		this.statusLock = new ReentrantReadWriteLock();
		this.users = users;
	}

	public void run() {
		try {
			go();
		} catch (Exception e) {
			System.out.printf("IOException on opening selector\n");
			e.printStackTrace();
			return;
		}
	}

	public void kill() {
		this.statusLock.writeLock().lock();
		runningStatus = false;
		this.statusLock.writeLock().unlock();
	}

	public void setUsers(Users users) {
		this.users = users;
	}

	public boolean running() 
	{
		boolean status = false;

		this.statusLock.readLock().lock();
		status = runningStatus;
		this.statusLock.readLock().unlock();

		return status;
	}

	private void go() throws Exception
	{
		int i = 0;
		int num = 0;
		ServerSocketChannel serverSocketChannel = null;
		Selector selector = null;
		ServerSocket ss = null;
		InetSocketAddress address = null;
		SelectionKey key = null;
		Set selectedKeys = null;
		Iterator it = null;
		SocketChannel sc = null;
		SelectionKey newKey = null;
		Packet packet = null;

		if (this.users == null) {
			System.err.printf("users not initialized]n");
			System.exit(3);
		}

		// Create a new selector
		selector = Selector.open();

		// Open a listener on each port, and register each one
		// with the selector
		for (i = 0; i < ports.length; i++) {
			serverSocketChannel = null;
			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);

			ss = null;
			ss = serverSocketChannel.socket();

			address = null;
			address = new InetSocketAddress(ports[i]);
			ss.bind(address);

			key = null;
			key = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

			System.out.println( "Going to listen on " + ports[i] );
		}

		while (this.running()) {
			num = selector.select();

			selectedKeys = null;
			selectedKeys = selector.selectedKeys();
			it = null;
			it = selectedKeys.iterator();

			while (it.hasNext()) {
				if (!this.running()) {
					break;
				}
				key = null;
				key = (SelectionKey)it.next();

				if ((key.readyOps() & SelectionKey.OP_ACCEPT)
						== SelectionKey.OP_ACCEPT) {
					/* Accept the new connection */
					serverSocketChannel = null;
					serverSocketChannel = (ServerSocketChannel)key.channel();

					sc = null;
					sc = serverSocketChannel.accept();
					sc.configureBlocking(false);

					/* Add the new connection to the selector */
					newKey = null;
					newKey = sc.register(selector, SelectionKey.OP_READ);
					it.remove();

					System.out.println("Got connection from "+sc);
				} else if ((key.readyOps() & SelectionKey.OP_READ)
						== SelectionKey.OP_READ) {
					/* Read the data */
					sc = null;
					sc = (SocketChannel)key.channel();
					packet = null;
					packet = Packet.receivePacket(sc);

					/* Process data */
					if (packet == null) {
						users.removeChannel(sc);
						/* doubly redundant */
						sc.close();
					} else if (packet.code == Code.QUIT) {
						users.removeName(packet.name);
						/* redundant */
						sc.close();
					} else if (packet.code == Code.SEND) {
						users.sendPacket(packet);
					} else if (packet.code == Code.ECHO) {
						Packet.sendPacket(packet, sc);
					} else if (packet.code == Code.BROADCAST) {
					} else if (packet.code == Code.LOGIN) {
						this.users.addConnection(packet.name, sc);
					}
				
					it.remove();
				}
			}
		}
	}

	static public void main( String args[] ) throws Exception {
		ServerListener listener = null;
		Thread thread = null;
		Users users = null;
		if (args.length <= 0) {
			System.err.println("Usage: java ServerListener port [port port ...]");
			System.exit(1);
		}

		int ports[] = new int[args.length];

		for (int i=0; i<args.length; ++i) {
			ports[i] = Integer.parseInt( args[i] );
		}

		users = new Users();
		listener = new ServerListener(ports, users);
		thread = new Thread(listener);
		thread.start();
	}
}
