# Sistema Distribuído Key-Value Store

Este projeto implementa um sistema distribuído que armazena pares chave-valor de forma replicada e consistente, similar ao Zookeeper, mas simplificado, utilizando TCP para comunicação.

## Funcionalidades

- **Três Servidores**: Um sistema composto por três servidores, com um atuando como líder para operações de PUT e todos capazes de responder a GETs.
- **Operações de Cliente**: Os clientes podem realizar operações de PUT para inserir dados e GET para recuperá-los, com um sistema de timestamp para consistência.
- **Comunicação TCP**: Utiliza o protocolo TCP para todas as comunicações entre clientes e servidores.

## Mensagens e Interações

- Mensagens específicas são exibidas na console para operações de INIT, PUT e GET, com um menu interativo para facilitar a interação do usuário.

## Demonstração do Projeto

- Realizei a demonstração do sistema, assegurando que todas as funcionalidades estão operando conforme o esperado.
- [Vídeo Demonstrativo](https://youtu.be/93vA1AKvPt8): Assista ao vídeo que eu criei, demonstrando a corretude e funcionalidade do sistema em um ambiente simulado.

---

Este README serve como uma visão geral do projeto que implementei seguindo os requisitos especificados pelo professor. O código fonte completo e detalhado, juntamente com o relatório técnico, estão disponíveis no repositório.
