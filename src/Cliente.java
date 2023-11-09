import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

public class Cliente {
	private static String[] serverIP = new String[3];
	private static int[] serverPort = new int[3];
	private static ConcurrentHashMap<String, Integer> timestampMap = new ConcurrentHashMap<>();

	public static class ThreadSendRequest extends Thread{
		private Mensagem requestMensagem;

		// Thread para envio de requisições.  
		public ThreadSendRequest(Mensagem mensagem) {
			this.requestMensagem = mensagem;
		}

		public void run() {
			try {

				int random = new Random().nextInt(3);
				Socket sendRequestSocket = new Socket(serverIP[random], serverPort[random]);
				OutputStream os = sendRequestSocket.getOutputStream();
				DataOutputStream writer = new DataOutputStream(os);
				InputStreamReader is = new InputStreamReader(sendRequestSocket.getInputStream());
				BufferedReader reader = new BufferedReader(is);

				// Acrescenta o IP e a porta do cliente na Mensagem e nesse momento envia para o servidor aleatório
				String clientIP = ((InetAddress) (sendRequestSocket.getLocalAddress())).getHostAddress();
				int clientPort = sendRequestSocket.getLocalPort();
				requestMensagem = new Mensagem(requestMensagem.getKey(), requestMensagem.getValue(), requestMensagem.getTimestamp(), requestMensagem.getRequest(), clientIP, clientPort);
				Gson gson = new Gson();
				writer.writeBytes(gson.toJson(requestMensagem) + "\n");

				// Fecha o canal de comunicação com o servidor após enviar a requisição e fica ouvindo na porta em que enviou a solicitação pare receber as respostas das requisições. Após aceitar um pedido de conexão, cria o mecanismo para receber a resposta
				sendRequestSocket.close();
				ServerSocket serverSocket = new ServerSocket(clientPort);
				Socket receiveRequestSocket = serverSocket.accept();
				is = new InputStreamReader(receiveRequestSocket.getInputStream());
				reader = new BufferedReader(is);

				// Recebe a resposta da requisição
				Mensagem responseMensagem = gson.fromJson(reader.readLine(), Mensagem.class);

				// Verifica qual é a resposta e imprime a mensagem adequada no console do cliente. Se for um "PUT_OK" ou um "GET" bem-sucedido, adiciona o timestamp do servidor ao mapa de hash
				if (responseMensagem.getRequest().equals("PUT_OK")) {
					System.out.println("\n\nPUT_OK key:" + responseMensagem.getKey() + " value:" + responseMensagem.getValue() + " timestamp:" + responseMensagem.getTimestamp() + " realizada no servidor " + serverIP[random] + ":" + serverPort[random] + "\n");
					timestampMap.put(responseMensagem.getKey(), responseMensagem.getTimestamp());
				} else if (responseMensagem.getRequest().equals("NULL")) {
					System.out.println("\n\nERRO: NULL\n");
				} else if (responseMensagem.getRequest().equals("TRY_OTHER_SERVER_OR_LATER")) {
					System.out.println("\n\nERRO: TRY_OTHER_SERVER_OR_LATER\n");
				} else {
					System.out.println("\n\nGET key:" + responseMensagem.getKey() + " value:" + responseMensagem.getValue() + " obtido do servidor " + serverIP[random] + ":" + serverPort[random] + ", meu timestamp " + timestampMap.get(responseMensagem.getKey()) + " e do servidor " + responseMensagem.getTimestamp() + "\n");
					timestampMap.put(responseMensagem.getKey(), responseMensagem.getTimestamp());
				}
				
				// Fecha os canais de comunicação com o servidor
				serverSocket.close();
				receiveRequestSocket.close();

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws UnknownHostException, IOException {

		System.out.print("\033[H\033[2J");
		System.out.flush();
		System.out.println("=== CLIENTE ===");
		System.out.println("\n=== MENU ===");
		System.out.println("1 - INIT");
		System.out.println("2 - PUT");
		System.out.println("3 - GET");
		System.out.println("============");

		@SuppressWarnings("resource")
		Scanner scanner = new Scanner(System.in);

		while (true) {
			System.out.print("\nMENU: ");

			int option = scanner.nextInt();
			scanner.nextLine();

			switch (option) {

			// 4. a) Incializa o cliente. Caso opte por usar os valores padrões, os IPs serão 127.0.0.1 e a porta do líder será 10099
			case 1:
				if (serverIP[0] != null) {
					System.out.println("\nCliente ja inicializado.");
				} else {
					System.out.print("\nDeseja entrar com os valores default? [Y/n] ");
					if (scanner.nextLine().toLowerCase().equals("n")) {
						for (int i = 0; i < 3; i++) {
							System.out.print("\nIP do servidor " + (int) (i + 1) + ": ");
							serverIP[i] = scanner.nextLine();
							System.out.print("Porta do servidor " + (int) (i + 1) + ": ");
							serverPort[i] = scanner.nextInt();
							scanner.nextLine();
						}
					} else {
						serverIP[0] = "127.0.0.1";
						serverIP[1] = "127.0.0.1";
						serverIP[2] = "127.0.0.1";
						serverPort[0] = 10097;
						serverPort[1] = 10098;
						serverPort[2] = 10099;
					}
				}
				break;

				// 4. b) Envio do PUT. Detalhes nos comentários
			case 2:
				if (serverIP[0] == null) {
					System.out.println("\nCliente nao incializado.");

					// Captura do teclado a key e value a ser inserida
				} else {
					System.out.print("\nChave: ");
					String key = scanner.nextLine();
					System.out.print("Valor: ");
					String value = scanner.nextLine();

					// Caso a chave ainda não exista, inicializa com o timestamp 0
					if (!timestampMap.containsKey(key)) {
						timestampMap.put(key, 0);
					}

					// Incrementa o timestamp em 1 e adiciona a chave no mapa com o novo timestamp
					timestampMap.put(key, timestampMap.get(key) + 1);
					
					// Cria a Mensagem de requisição, contendo a key e value e abre uma thread para tratá-la.
					Mensagem requestMensagem = new Mensagem(key, value, timestampMap.get(key), "PUT");
					new ThreadSendRequest(requestMensagem).start();

					break;
				}

				// 4. c) Envio do GET. Detalhes nos comentários
			case 3:
				if (serverIP[0] == null) {
					System.out.println("\nCliente nao incializado.");

					// Captura do teclado a key a ser procurada
				} else {
					System.out.print("\nChave: ");
					String key = scanner.nextLine();

					if (!timestampMap.containsKey(key)) {
						timestampMap.put(key, 0);
					}

					// Cria uma Mensagem de requisição contendo a key a ser procurada e inicia uma thread.
					Mensagem requestMensagem = new Mensagem(key, null, timestampMap.get(key), "GET");
					new ThreadSendRequest(requestMensagem).start();
				}
			}
		}
	}
}
