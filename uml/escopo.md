# ZapIF — Documento de Escopo

## 1. Visão Geral

ZapIF é um sistema de chat em tempo real baseado em arquitetura cliente-servidor TCP. O servidor é um processo Java puro com banco SQLite; o cliente é uma aplicação desktop JavaFX. A comunicação usa um protocolo de texto simples delimitado por `|`.

---

## 2. Telas (Interfaces do Usuário)

### Tela 1 — Login / Cadastro (`login.fxml`)

**Objetivo:** autenticar ou registrar um usuário antes de acessar o chat.

| Elemento | Tipo | Descrição |
|---|---|---|
| Campo "Nome de usuário" | TextField | 3–24 caracteres, sem `\|` |
| Campo "Senha" | PasswordField | 6–64 caracteres |
| Botão "Entrar" | Button | Envia `LOGIN\|nome\|senha` ao servidor |
| Botão "Cadastrar" | Button | Envia `REGISTER\|nome\|senha` ao servidor |
| Label de status | Label | Exibe erros de validação, estado da conexão e feedback do servidor |

**Fluxos:**
- Cadastro bem-sucedido → label exibe "Cadastro realizado! Faça login"
- Login bem-sucedido → transição automática para a Tela 2
- Erro (credenciais inválidas, usuário já existe) → label exibe a mensagem de erro
- Sem conexão → botões desabilitados, label exibe "Sem conexão — tentando reconectar..."
- Timeout de 10 s sem resposta → label exibe aviso e reabilita botões

---

### Tela 2 — Chat (`chat.fxml`)

**Objetivo:** permitir que o usuário envie e receba mensagens em salas de chat.

| Elemento | Tipo | Descrição |
|---|---|---|
| Lista de salas | ListView | Exibe as salas disponíveis; clique troca de sala |
| Lista de mensagens | ListView | Exibe o histórico e mensagens em tempo real da sala ativa |
| Campo de mensagem | TextField | Texto a enviar; `Enter` ou botão dispara o envio |
| Botão "Enviar" | Button | Envia `MSG\|sala\|usuario\|texto` |
| Banner offline | HBox (vermelho) | Aparece quando a conexão cai; some ao reconectar |

**Fluxos:**
- Ao entrar na tela → sala 0 selecionada automaticamente, histórico carregado
- Troca de sala → lista de mensagens limpa, novo histórico carregado
- Mensagem recebida → adicionada ao final da lista, scroll automático para baixo
- Conexão cai → banner vermelho aparece, campo e botão desabilitados
- Reconexão → banner some, campo e botão reabilitados

---

## 3. Casos de Uso

| ID | Nome | Ator | Descrição resumida |
|---|---|---|---|
| UC01 | Cadastrar conta | Usuário não autenticado | Cria uma nova conta com nome e senha |
| UC02 | Fazer login | Usuário não autenticado | Autentica com credenciais existentes |
| UC03 | Visualizar salas | Usuário autenticado | Vê a lista de salas disponíveis |
| UC04 | Entrar em sala | Usuário autenticado | Seleciona uma sala e carrega o histórico |
| UC05 | Enviar mensagem | Usuário autenticado | Envia texto para a sala atual |
| UC06 | Receber mensagem em tempo real | Usuário autenticado | Recebe mensagens de outros usuários via broadcast |
| UC07 | Visualizar histórico | Usuário autenticado | Carrega as últimas 50 mensagens ao entrar na sala |
| UC08 | Trocar de sala | Usuário autenticado | Sai da sala atual e entra em outra |
| UC09 | Desconectar | Usuário autenticado | Fecha o cliente; sessão liberada no servidor |
| UC10 | Validar credenciais | Servidor | Verifica nome/senha com PBKDF2 no banco |
| UC11 | Persistir mensagem | Servidor | Salva mensagem no SQLite antes do broadcast |
| UC12 | Transmitir mensagem (broadcast) | Servidor | Envia a mensagem para todos na sala |
| UC13 | Heartbeat PING/PONG | Servidor | Detecta clientes inativos a cada 20 s |
| UC14 | Reconectar automaticamente | Cliente | Tenta reconectar ao servidor a cada 5 s após queda |

---

## 4. Arquitetura

### Visão em camadas

```
┌─────────────────────────────────────────────────────┐
│                  CLIENTE (JavaFX)                   │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │  client  │  │ client.ui    │  │ client.model  │ │
│  │  .Main   │  │ ControladorL │  │ Mensagem      │ │
│  │          │  │ ControladorC │  │ Usuario       │ │
│  └──────────┘  └──────────────┘  └───────────────┘ │
│                ┌──────────────────────────────────┐ │
│                │       client.network             │ │
│                │  Conexao  OuvinteMensagem         │ │
│                │           OuvinteStatusConexao   │ │
│                └──────────────────────────────────┘ │
└───────────────────────┬─────────────────────────────┘
                        │ TCP Socket (porta 5001)
                        │ protocolo texto com "|"
┌───────────────────────┴─────────────────────────────┐
│                  SERVIDOR (Java SE)                 │
│  ┌───────────┐  ┌──────────────────┐  ┌──────────┐ │
│  │ Servidor  │  │ManipuladorCliente│  │  Sala    │ │
│  │ (main +   │  │ (1 thread/client)│  │(broadcast│ │
│  │  pool)    │  └──────────────────┘  │  list)   │ │
│  └───────────┘  ┌──────────────────┐  └──────────┘ │
│                 │  RegistroSessao  │               │
│                 │  BancoDados      │               │
│                 └──────────────────┘               │
│                        │ JDBC                      │
│                 ┌──────┴──────┐                    │
│                 │  SQLite     │                    │
│                 │  chat.db    │                    │
│                 └─────────────┘                    │
└─────────────────────────────────────────────────────┘
```

### Pacotes do cliente

| Pacote | Responsabilidade |
|---|---|
| `client` | Entry point (`Main`), ciclo de vida da aplicação JavaFX |
| `client.ui` | Controllers das telas (FXML + lógica de apresentação) |
| `client.network` | Conexão TCP, retry automático, entrega de mensagens na thread JavaFX |
| `client.model` | Objetos de domínio (`Mensagem`, `Usuario`) |

### Pacotes do servidor

| Pacote | Responsabilidade |
|---|---|
| `server` | Todos os componentes do servidor |
| `Servidor` | Entry point, `ServerSocket`, pool de threads |
| `ManipuladorCliente` | Uma thread por cliente; interpreta o protocolo, valida e roteia |
| `Sala` | Agrupa clientes conectados, executa broadcast thread-safe |
| `RegistroSessao` | Controle de sessões únicas (impede login duplo) |
| `BancoDados` | Acesso ao SQLite: usuários, salas, histórico de mensagens |

---

## 5. Protocolo de Comunicação

Texto puro separado por `|`, uma mensagem por linha (`\n`).

| Mensagem | Direção | Descrição |
|---|---|---|
| `REGISTER\|nome\|senha` | cliente → servidor | Cadastrar usuário |
| `LOGIN\|nome\|senha` | cliente → servidor | Autenticar |
| `JOIN\|sala` | cliente → servidor | Entrar em sala |
| `MSG\|sala\|nome\|texto` | ambos | Enviar / receber mensagem |
| `PONG` | cliente → servidor | Resposta ao heartbeat |
| `OK\|contexto` | servidor → cliente | Confirmação (REGISTER, LOGIN) |
| `ROOMS\|sala1,sala2,...` | servidor → cliente | Lista de salas após login |
| `HISTORY\|sala\|msg1\|...` | servidor → cliente | Histórico ao entrar na sala |
| `PING` | servidor → cliente | Heartbeat a cada 20 s |
| `ERROR\|motivo` | servidor → cliente | Erro descritivo |

---

## 6. Banco de Dados

```sql
usuarios  (id, nome UNIQUE, senha_hash, salt)
salas     (id, nome UNIQUE)
mensagens (id, sala_nome, usuario, texto, timestamp)
```

- Senhas: **PBKDF2-HMAC-SHA256** + salt aleatório por usuário (100 000 iterações)
- Salas padrão criadas na inicialização: `geral`, `off-topic`, `ajuda`
- Histórico: últimas 50 mensagens por sala, retornadas em ordem cronológica

---

## 7. Restrições e Limites

| Campo | Regra |
|---|---|
| Nome de usuário | 3–24 caracteres, sem `\|` |
| Senha | 6–64 caracteres |
| Texto de mensagem | 1–500 caracteres, `\|` removido automaticamente |
| Clientes simultâneos | máximo 100 (pool de threads do servidor) |
| Histórico por sala | últimas 50 mensagens |
| Timeout de login | 10 s sem resposta → reabilita botões |
| Heartbeat | PING a cada 20 s; timeout de leitura em 45 s |
| Retry de conexão | a cada 5 s após queda |

---

## 8. Fora do Escopo (versão atual)

- Mensagens privadas (DM) entre usuários
- Criação/exclusão de salas pelo usuário
- Upload de arquivos ou imagens
- Notificações de desktop
- Criptografia da conexão (TLS/SSL)
- Interface web ou mobile
- Autenticação com OAuth / SSO
- Administração de usuários (banir, moderar)
