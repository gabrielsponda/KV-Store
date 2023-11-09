import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

public class Servidor {
	private static String serverIP;
	private static int serverPort;
	private static String leaderIP;
	private static int leaderPort;
	private static Boolean isLeader = false;
	private static String[] secondaryIP = new String[2];
	private static int[] secondaryPort = new int[2];
	
	// A estrutura escolhida para armazenar foi um concurrent hash map, que guarda uma key e uma lista. A ideia é que a lista guarde [value, timestamp, replication]
	private static ConcurrentHashMap<String, List<Object>> dataMap = new ConcurrentHashMap<>();

	private static class ThreadReplication extends Thread {
		private int server;
		private Mensagem replicationMensagem;

		// Inicia o processo de replicação
		public ThreadReplication(int server, Mensagem mensagem) {
			this.server = server;
			this.replicationMensagem = mensagem;
		}

		public void run() {
			try {
				
				// Cria o canal de comunicação com o servidor subordinado e envia como uma Mensagem os dados
				Socket socket = new Socket(secondaryIP[server], secondaryPort[server]);
				OutputStream os = socket.getOutputStream();
				DataOutputStream writer = new DataOutputStream(os);
				Gson gson = new Gson();
				writer.writeBytes(gson.toJson(replicationMensagem) + "\n");
				socket.close();

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private static class ThreadReceiveRequest extends Thread {
		private Socket socket;

		// Permite que o servidor receba e responda simultaneamente as requisições dos clientes e dos outros servidores
		public ThreadReceiveRequest(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			try {
				
				// Cria o mecanismos de comunicação com o requisitante, seja um cliente ou outro servidor
				InputStreamReader is = new InputStreamReader(socket.getInputStream());
				BufferedReader reader = new BufferedReader(is);
				Gson gson = new Gson();
				Mensagem requestMensagem = gson.fromJson(reader.readLine(), Mensagem.class);

				OutputStream os;
				DataOutputStream writer;
				Mensagem responseMensagem = null;

				switch (requestMensagem.getRequest()) {

				// 5. c) Recebe a requisição PUT. Detalhes nos comentários
				case "PUT":

					// Caso o servidor não seja o líder, encaminha a requisição para o servidor líder
					if (!isLeader) {
						System.out.println("\nEncaminhando PUT key:" + requestMensagem.getKey() + " value:"
								+ requestMensagem.getValue());

						// Cria um canal de comunicação com o servidor líder e envia a mensagem
						Socket socket = new Socket(leaderIP, leaderPort);
						os = socket.getOutputStream();
						writer = new DataOutputStream(os);
						writer.writeBytes(gson.toJson(requestMensagem) + "\n");
						socket.close();
						
					// Caso o servidor seja o líder, processa a requisição
					} else {
						System.out.println("\nCliente " + requestMensagem.getClientIP() + ":"
								+ requestMensagem.getClientPort() + " PUT key:" + requestMensagem.getKey() + " value:"
								+ requestMensagem.getValue());

						// 5. c) 1. Incializa uma lista e verifica se a chave existe na tabela hash, caso exista, copia o valor do timestamp para a lista. Coloca a key na lista, verifica qual é o maior timestamp entre do servidor ou do cliente somado de 1 e o coloca na lista e adiciona à tabela com informações atualizadas
						List<Object> input = Arrays.asList("", 0, 0);
						if (dataMap.containsKey(requestMensagem.getKey())) {
							input.set(1, dataMap.get(requestMensagem.getKey()).get(1));
						}
						input.set(0, requestMensagem.getValue());
						input.set(1, Integer.max((Integer) input.get(1) + 1, requestMensagem.getTimestamp()));
						dataMap.put(requestMensagem.getKey(), input);
						
						// 5. c) 2. Inicia duas threads de replicação
						Mensagem replicationMensagem = new Mensagem(requestMensagem.getKey(), (String) dataMap.get(requestMensagem.getKey()).get(0),
								(Integer) dataMap.get(requestMensagem.getKey()).get(1), "REPLICATION");
						new ThreadReplication(0, replicationMensagem).start();
						new ThreadReplication(1, replicationMensagem).start();
						
						// Quando os servidores subordinados recebem a requisição de replicação e armazenam os dados, devolvem "REPLICATION_OK" e então o servidor líder atualiza o campo replication da lista associada à key. Enquanto não receber duas confirmações, fica aguardando
						while ((int) dataMap.get(requestMensagem.getKey()).get(2) < 2) {
							sleep(100);
						}

						System.out.println("\nEnviando PUT_OK ao cliente " + requestMensagem.getClientIP() + ":"
								+ requestMensagem.getClientPort() + " da key:" + requestMensagem.getKey() + " ts:"
								+ (Integer) dataMap.get(requestMensagem.getKey()).get(1));
						
						// 5. c) 3. Cria a mensagem de "PUT_OK" a ser enviada para o cliente
						responseMensagem = new Mensagem(requestMensagem.getKey(), (String) dataMap.get(requestMensagem.getKey()).get(0), (Integer) dataMap.get(requestMensagem.getKey()).get(1),
								"PUT_OK");
						
						// Conecta no server socket aberto para o cliente para fazer o envio da confirmação diretamente para ele
						Socket socket = new Socket(requestMensagem.getClientIP(), requestMensagem.getClientPort());
						os = socket.getOutputStream();
						writer = new DataOutputStream(os);
						writer.writeBytes(gson.toJson(responseMensagem) + "\n");
					}
					break;
					
				// 5. d) Recebe a requisição REPLICATION
				case "REPLICATION":
					
					// "TRY_OTHER_SERVER_OR_LATER" Essa condição é usada para testar o atraso da replicação, simulando a situação em que o servidor líder já armazenou a key e value, mas os subordinados ainda não. Usando qualquer outra chave que não "teste", o atraso é ignorado
					if (requestMensagem.getKey().equals("teste")) {
						sleep(30000);
					}
					
					System.out.println("\nREPLICATION key:" + requestMensagem.getKey() + " value:"
							+ requestMensagem.getValue() + " ts:" + requestMensagem.getTimestamp());

					// De forma similar à requisição PUT, cria uma lista, edita os campos e armazena na tabela hash local
					List<Object> input = Arrays.asList("", 0, 0);
					if (dataMap.containsKey(requestMensagem.getKey())) {
						input.set(1, dataMap.get(requestMensagem.getKey()).get(1));
					}
					input.set(0, requestMensagem.getValue());
					input.set(1, requestMensagem.getTimestamp());
					dataMap.put(requestMensagem.getKey(), input);

					// Cria um canal de comunicação com o líder e responde "REPLICATION_OK"
					socket = new Socket(leaderIP, leaderPort);
					os = socket.getOutputStream();
					writer = new DataOutputStream(os);
					responseMensagem = new Mensagem(requestMensagem.getKey(),
							(String) dataMap.get(requestMensagem.getKey()).get(0),
							(Integer) dataMap.get(requestMensagem.getKey()).get(1), "REPLICATION_OK");
					writer.writeBytes(gson.toJson(responseMensagem) + "\n");
					break;

				// 5. e) Recebe o "REPLICATION_OK" e atualiza o campo replication na lista relacionada à key. Após duas atualizações, o loop de espera acaba e o líder prossegue o PUT
				case "REPLICATION_OK":
					dataMap.get(requestMensagem.getKey()).set(2, (int) dataMap.get(requestMensagem.getKey()).get(2) + 1);
					break;

				// 5. f) Recebe a requisição GET
				case "GET":
					
					// Cria uma lista e copia os valores para ela, caso a chave exista
					List<Object> requestedData = Arrays.asList("", 0, 0);
					if (dataMap.containsKey(requestMensagem.getKey())) {
						requestedData = dataMap.get(requestMensagem.getKey());
					}

					// Cabeçalho das respostas
					String responseHeader = "\nCliente " + requestMensagem.getClientIP() + ":"
							+ requestMensagem.getClientPort() + " GET key:" + requestMensagem.getKey() + " ts:"
							+ requestMensagem.getTimestamp() + ". Meu ts e " + requestedData.get(1)
							+ ", portanto devolvendo ";

					// Verifica se o value é nulo, ou seja, se existe a chave na tabela hash, já que a estrutura criada na linha 175 permanece com o value como uma string em branco, caso a chave não exista. Cria a mensagem adequada contendo o erro "NULL"
					if (((Integer) requestedData.get(1)) < requestMensagem.getTimestamp()) {
						System.out.println(responseHeader + "TRY_OTHER_SERVER_OR_LATER");
						responseMensagem = new Mensagem(requestMensagem.getKey(), (String) requestedData.get(0),
								(Integer) requestedData.get(1), "TRY_OTHER_SERVER_OR_LATER");
						
					// Caso a chave exista, verifica se o timestamp do servidor é menor que o timestamp recebido. Caso seja, cria a mensagem com o erro "TRY_OTHER_SERVER_OR_LATER"
					} else if (requestedData.get(0).equals("")) {
						System.out.println(responseHeader + "NULL");
						responseMensagem = new Mensagem(requestMensagem.getKey(), (String) requestedData.get(0),
								(Integer) requestedData.get(1), "NULL");
						
					// Caso nenhum dos erros ocorram, cria a mensagem adequada ao cliente, contendo o value buscado
					} else {
						System.out.println(responseHeader + requestedData.get(0));
						responseMensagem = new Mensagem(requestMensagem.getKey(), (String) requestedData.get(0),
								(Integer) requestedData.get(1), (String) requestedData.get(0));
					}

					// Envia a mensagem criada ao cliente
					socket = new Socket(requestMensagem.getClientIP(), requestMensagem.getClientPort());
					os = socket.getOutputStream();
					writer = new DataOutputStream(os);
					writer.writeBytes(gson.toJson(responseMensagem) + "\n");
					break;
				}
			}

			catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws IOException {
		System.out.print("\033[H\033[2J");
		System.out.flush();
		System.out.println("=== SERVIDOR ===");

		Scanner scanner = new Scanner(System.in);
		
		// 5. a) Incializa o servidor. Primeiro informa se o servidor é o líder e então escolhe entre os dados padrões. Para os servidores que não são o líder, a porta deve ser informada manualmente
		System.out.print("\nEsse servidor e o lider? [Y/n] ");
		if (scanner.nextLine().toUpperCase().equals("Y")) {
			System.out.print("\nDeseja utilizar os dados default? [Y/n] ");
			if (scanner.nextLine().toUpperCase().equals("Y")) {
				isLeader = true;
				serverIP = "127.0.0.1";
				serverPort = 10099;
				leaderIP = serverIP;
				leaderPort = serverPort;
				secondaryIP[0] = "127.0.0.1";
				secondaryPort[0] = 10097;
				secondaryIP[1] = "127.0.0.1";
				secondaryPort[1] = 10098;
			} else {
				isLeader = true;
				System.out.print("\nIP: ");
				serverIP = scanner.nextLine();
				System.out.print("Porta: ");
				serverPort = scanner.nextInt();
				scanner.nextLine();
				leaderIP = serverIP;
				leaderPort = serverPort;
				System.out.print("\nIP subordinado 1: ");
				secondaryIP[0] = scanner.nextLine();
				System.out.print("Porta subordinado 1: ");
				secondaryPort[0] = scanner.nextInt();
				scanner.nextLine();
				System.out.print("\nIP subordinado 2: ");
				secondaryIP[1] = scanner.nextLine();
				System.out.print("Porta subordinado 2: ");
				secondaryPort[1] = scanner.nextInt();
				scanner.nextLine();
			}
		} else {
			System.out.print("\nDeseja utilizar os dados (exceto a porta) default? [Y/n] ");
			if (scanner.nextLine().toUpperCase().equals("Y")) {
				serverIP = "127.0.0.1";
				System.out.print("\nPorta: ");
				serverPort = scanner.nextInt();
				scanner.nextLine();
				leaderIP = serverIP;
				leaderPort = 10099;
			} else {
				System.out.print("\nIP: ");
				serverIP = scanner.nextLine();
				System.out.print("Porta: ");
				serverPort = scanner.nextInt();
				scanner.nextLine();
				System.out.print("\nIP lider: ");
				leaderIP = scanner.nextLine();
				System.out.print("Porta lider: ");
				leaderPort = scanner.nextInt();
				scanner.nextLine();
			}
		}
		
		// Fecha o scanner para liberar recursos
		scanner.close();
		
		// 5. b) Para toda requisição, uma thread é criada e permite que o servidor receba e responda simultaneamente requisições dos clientes 
		@SuppressWarnings("resource")
		ServerSocket serverSocket = new ServerSocket(serverPort);
		while (true) {
			Socket socket = serverSocket.accept();
			new ThreadReceiveRequest(socket).start();
		}

	}
}
