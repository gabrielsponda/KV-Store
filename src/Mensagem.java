
public class Mensagem {
	private String key;
	private String value;
	private Integer timestamp;
	private String request;
	private String clientIP;
	private int clientPort;
	
	// Construtor usado para enviar requisições PUT e pela thread ThreadGet
	public Mensagem(String key, String value, Integer timestamp, String request, String clientIP, int clientPort) {
		this.key = key;
		this.value = value;
		this.timestamp = timestamp;
		this.request = request;
		this.clientIP = clientIP;
		this.clientPort = clientPort;
	}
	
	// Construtor usado para todos os outros envios de requisições, pelo cliente e pelo servidor
	public Mensagem(String key, String value, Integer timestamp, String request) {
		this.key = key;
		this.value = value;
		this.timestamp = timestamp;
		this.request = request;
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public Integer getTimestamp() {
		return timestamp;
	}

	public String getRequest() {
		return request;
	}

	public String getClientIP() {
		return clientIP;
	}

	public int getClientPort() {
		return clientPort;
	}
}
