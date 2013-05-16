package com.jini.server;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.webapp.WebAppContext;

public class MainServer extends JFrame {

	private Server server;

	public MainServer() {
		initialize();
	}

	private void initialize() {
		JLabel label = new JLabel("Path");
		final JTextField textField = new JTextField();
		final JFileChooser fc = new JFileChooser();
		JButton browseButton = new JButton("Browse");
		final JButton findButton = new JButton("Start");
		JButton cancelButton = new JButton("Close");
		final JTextArea jTextArea = new JTextArea();
		jTextArea.setWrapStyleWord(true);
		MessageBox.setTextArea(jTextArea);
		setTitle("Jini");

		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		browseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int returnVal = fc.showOpenDialog(MainServer.this);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = fc.getSelectedFile();
					textField.setText(file.getAbsolutePath());
				}
			}
		});

		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		});
		findButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (server != null) {
					try {
						textField.setText("");
						textField.setEnabled(true);
						server.stop();
						MessageBox.addMessage("Server stopped");
						server = null;
					} catch (Exception e) {
						e.printStackTrace();
					}
					findButton.setText("Start");
				} else {
					Thread t = new Thread(new Runnable() {
						public void run() {
							try {
								WebAppContext context = new WebAppContext();
								context.setDescriptor("./WEB-INF/web.xml");
								context.setResourceBase("./");
								context.setContextPath("/jini");

								String text = textField.getText();
								server = new Server(9090);
								CustomResourceHandler customResourceHandler = new CustomResourceHandler(
										text);
								context.setServer(server);
								server.setHandler(context);
								HandlerCollection handler = new HandlerCollection();
								handler.addHandler(customResourceHandler);
								handler.addHandler(context);
								server.setHandler(customResourceHandler);								
								server.start();
								MessageBox.addMessage("Started Server on port 9090");
								server.join();								
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					});
					textField.setEnabled(false);
					t.start();
					findButton.setText("Stop");
				}
			}
		});
		textField.setPreferredSize(new Dimension(200, 20));

		GroupLayout layout = new GroupLayout(getContentPane());
		getContentPane().setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);

		SequentialGroup col1 = layout.createSequentialGroup()
				.addComponent(label).addComponent(textField)
				.addComponent(browseButton);		

		jTextArea.setPreferredSize(new Dimension(200, 200));

		SequentialGroup col2 = layout.createSequentialGroup().addComponent(
				jTextArea);

		SequentialGroup col3 = layout.createSequentialGroup()
				.addComponent(findButton).addComponent(cancelButton);

		layout.setHorizontalGroup(layout.createParallelGroup().addGroup(col1)
				.addGroup(col2).addGroup(col3));

		ParallelGroup rrow1 = layout.createParallelGroup().addComponent(label)
				.addComponent(textField).addComponent(browseButton);

		ParallelGroup rrow2 = layout.createParallelGroup().addComponent(
				jTextArea);

		ParallelGroup rrow3 = layout.createParallelGroup()
				.addComponent(findButton).addComponent(cancelButton);

		layout.setVerticalGroup(layout.createSequentialGroup().addGroup(rrow1)
				.addGroup(rrow2).addGroup(rrow3));

		pack();
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
//		setResizable(false);
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		java.awt.EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager
							.getCrossPlatformLookAndFeelClassName());
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				new MainServer().setVisible(true);
			}
		});
	}

}
