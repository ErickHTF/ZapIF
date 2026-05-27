package server;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Acesso ao banco SQLite. Cada método abre e fecha a própria conexão.
 * Senhas armazenadas com PBKDF2-HMAC-SHA256 + salt por usuário.
 */
public class Database {
    private static final String DB_URL      = "jdbc:sqlite:chat.db";
    private static final int    PBKDF2_ITER = 100_000; // mínimo recomendado pelo OWASP
    private static final int    SALT_BYTES  = 16;
    private static final int    HASH_BITS   = 256;

    public static void init() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS usuarios (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    nome       TEXT UNIQUE NOT NULL,
                    senha_hash TEXT NOT NULL,
                    salt       TEXT NOT NULL
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS salas (
                    id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    nome TEXT UNIQUE NOT NULL
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mensagens (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    sala_nome TEXT NOT NULL,
                    usuario   TEXT NOT NULL,
                    texto     TEXT NOT NULL,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO salas (nome) VALUES (?)")) {
                for (String r : new String[]{"geral", "off-topic", "ajuda"}) {
                    ps.setString(1, r);
                    ps.executeUpdate();
                }
            }
            System.out.println("Banco de dados pronto.");
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao inicializar banco: " + e.getMessage(), e);
        }
    }

    /** @return true se criado, false se nome já existe (UNIQUE constraint) */
    public static boolean register(String nome, String senha) {
        String salt = generateSalt();
        String hash = pbkdf2(senha, salt);
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO usuarios (nome, senha_hash, salt) VALUES (?, ?, ?)")) {
            ps.setString(1, nome);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static boolean login(String nome, String senha) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT senha_hash, salt FROM usuarios WHERE nome = ?")) {
            ps.setString(1, nome);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                return rs.getString("senha_hash").equals(pbkdf2(senha, rs.getString("salt")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Usado para validar JOINs — impede criação de salas fantasma em memória. */
    public static boolean roomExists(String nome) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM salas WHERE nome = ?")) {
            ps.setString(1, nome);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<String> getRooms() {
        List<String> list = new ArrayList<>();
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT nome FROM salas ORDER BY nome")) {
            while (rs.next()) list.add(rs.getString("nome"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Retorna as N mensagens mais recentes em ordem cronológica.
     * A subquery pega as últimas em DESC; a query externa reordena em ASC para exibição.
     */
    public static List<String> getHistory(String sala, int limit) {
        List<String> msgs = new ArrayList<>();
        String sql = """
            SELECT usuario, texto FROM (
                SELECT id, usuario, texto FROM mensagens
                WHERE sala_nome = ? ORDER BY id DESC LIMIT ?
            ) ORDER BY id ASC
        """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sala);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    msgs.add(rs.getString("usuario") + ": " + rs.getString("texto"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return msgs;
    }

    /** Chamado antes do broadcast para garantir que a mensagem está persistida. */
    public static void saveMessage(String sala, String usuario, String texto) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO mensagens (sala_nome, usuario, texto) VALUES (?, ?, ?)")) {
            ps.setString(1, sala);
            ps.setString(2, usuario);
            ps.setString(3, texto);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // -- internals -------------------------------------------------------------

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private static String pbkdf2(String password, String saltB64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltB64);
            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, PBKDF2_ITER, HASH_BITS);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            spec.clearPassword(); // limpa a senha da memória o quanto antes
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Erro no hash de senha", e);
        }
    }
}
