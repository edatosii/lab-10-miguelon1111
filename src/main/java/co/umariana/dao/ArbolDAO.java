package co.umariana.dao;

import co.umariana.conexion.ConexionBD;
import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.Persona;
import co.umariana.modelo.ResumenArbol;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO (Data Access Object): la única clase que habla con la base de datos. Guarda los árboles genealógicos
 * en las tablas "arboles" y "miembros" (ver db/schema.sql). Se construye igual que EstudianteDAO del laboratorio 9.
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 4)
 * ------------------------------------------
 * 1. Cada método marcado con "TODO" lanza UnsupportedOperationException. Reemplace esa línea por su
 *    implementación con JDBC.
 * 2. NO modifique las pruebas (src/test). Las pruebas NO necesitan MySQL: usan una base de datos H2 en memoria.
 * 3. Esta etapa usa las anteriores: para guardar recorra arbol.miembros(); para cargar, reconstruya el árbol con
 *    new ArbolGenealogico(sujeto) y arbol.agregar(...).
 *
 * REGLAS (las pruebas las verifican)
 *  - Los métodos NO lanzan SQLException: la capturan, imprimen el mensaje con System.out.println y devuelven
 *    un valor "de error" (-1, false, lista vacía o null).
 *  - Use SIEMPRE PreparedStatement con parámetros (?), nunca concatene datos dentro del SQL.
 *  - Guardar un árbol son VARIAS sentencias (el árbol y todos sus miembros): hágalo en una TRANSACCIÓN
 *    (con.setAutoCommit(false); ... con.commit(); y con.rollback() si algo falla) para que nunca quede
 *    guardado "a medias". Antes de cerrar la conexión vuelva a dejar setAutoCommit(true).
 *  - El id de cada fila lo genera la base de datos (AUTO_INCREMENT).
 *  - Los miembros se guardan en el orden de arbol.miembros(): así, al leerlos ORDER BY id, cada persona
 *    aparece después de la que necesita para poder agregarla al árbol.
 */
public class ArbolDAO {

    // =====================================================================
    // ETAPA 4.1. Guardar un árbol nuevo (INSERT en dos tablas, con transacción)
    // =====================================================================

    /**
     * Guarda un árbol nuevo: una fila en "arboles" (con el nombre) y una fila en "miembros" por cada persona.
     *
     * @return el id que la base de datos le dio al árbol; -1 si el nombre es nulo, está en blanco o tiene más
     *         de 100 caracteres, si el árbol es null, o si hubo un error (en ese caso NO queda nada guardado)
     */
    public long guardar(String nombre, ArbolGenealogico arbol) {
        if (nombre == null || nombre.isBlank() || nombre.length() > 100 || arbol == null) {
            return -1;
        }

        Connection con = null;
        try {
            con = ConexionBD.conectar();
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO arboles (nombre) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, nombre);
                ps.executeUpdate();
                long id;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("La base de datos no devolvió el id del árbol.");
                    }
                    id = keys.getLong(1);
                }
                insertarMiembros(con, id, arbol);
                con.commit();
                return id;
            } catch (SQLException e) {
                rollback(con);
                System.out.println(e.getMessage());
                return -1;
            } finally {
                restaurarAutoCommit(con);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return -1;
        } finally {
            cerrar(con);
        }
    }

    // =====================================================================
    // ETAPA 4.2. Listar (SELECT con JOIN y COUNT)
    // =====================================================================

    /**
     * @return un resumen por cada árbol guardado (id, nombre, cuántos miembros tiene y cuándo se creó),
     *         ordenados por id; lista vacía si no hay árboles o si hay un error
     */
    public List<ResumenArbol> listar() {
        String sql = "SELECT a.id, a.nombre, a.creado, COUNT(m.id) AS total "
                + "FROM arboles a LEFT JOIN miembros m ON m.arbol_id = a.id "
                + "GROUP BY a.id, a.nombre, a.creado ORDER BY a.id";
        List<ResumenArbol> arboles = new ArrayList<>();
        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                arboles.add(new ResumenArbol(
                        rs.getLong("id"),
                        rs.getString("nombre"),
                        rs.getInt("total"),
                        rs.getTimestamp("creado").toLocalDateTime()));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return arboles;
    }

    // =====================================================================
    // ETAPA 4.3. Cargar un árbol (SELECT y reconstrucción del árbol)
    // =====================================================================

    /**
     * @return el árbol con ese id, reconstruido con todos sus miembros; null si no existe ese id o si hay un error
     */
    public ArbolGenealogico cargar(long id) {
        String sql = "SELECT posicion, nombre, nacimiento, fallecimiento "
                + "FROM miembros WHERE arbol_id = ? ORDER BY id";
        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ArbolGenealogico arbol = new ArbolGenealogico(persona(rs));
                while (rs.next()) {
                    arbol.agregar(persona(rs));
                }
                return arbol;
            }
        } catch (SQLException | IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    // =====================================================================
    // ETAPA 4.4. Actualizar (reemplazar los miembros de un árbol, con transacción)
    // =====================================================================

    /**
     * Guarda los cambios de un árbol ya existente (por ejemplo, después de agregar o eliminar miembros):
     * deja en la base de datos exactamente los miembros de {@code arbol}. El nombre del árbol NO cambia.
     *
     * @return true si se actualizó; false si no existe un árbol con ese id, si el árbol es null o si hubo un
     *         error (en ese caso NO se modifica nada: ni se borran los miembros anteriores)
     */
    public boolean actualizar(long id, ArbolGenealogico arbol) {
        if (arbol == null) {
            return false;
        }

        Connection con = null;
        try {
            con = ConexionBD.conectar();
            con.setAutoCommit(false);
            try (PreparedStatement existe = con.prepareStatement("SELECT id FROM arboles WHERE id = ?")) {
                existe.setLong(1, id);
                try (ResultSet rs = existe.executeQuery()) {
                    if (!rs.next()) {
                        return false;
                    }
                }

                try (PreparedStatement borrar = con.prepareStatement("DELETE FROM miembros WHERE arbol_id = ?")) {
                    borrar.setLong(1, id);
                    borrar.executeUpdate();
                }
                insertarMiembros(con, id, arbol);
                con.commit();
                return true;
            } catch (SQLException e) {
                rollback(con);
                System.out.println(e.getMessage());
                return false;
            } finally {
                restaurarAutoCommit(con);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        } finally {
            cerrar(con);
        }
    }

    // =====================================================================
    // ETAPA 4.5. Eliminar (DELETE)
    // =====================================================================

    /**
     * Elimina el árbol y todos sus miembros (la base de datos borra los miembros sola: ON DELETE CASCADE).
     *
     * @return true si se eliminó; false si no existía ese id o si hubo un error
     */
    public boolean eliminar(long id) {
        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement("DELETE FROM arboles WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    private static void insertarMiembros(Connection con, long arbolId, ArbolGenealogico arbol) throws SQLException {
        String sql = "INSERT INTO miembros "
                + "(arbol_id, posicion, nombre, nacimiento, fallecimiento) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (var miembro : arbol.miembros()) {
                ps.setLong(1, arbolId);
                ps.setString(2, miembro.getPosicion());
                ps.setString(3, miembro.getNombre());
                ps.setInt(4, miembro.getNacimiento());
                if (miembro.getFallecimiento() == null) {
                    ps.setNull(5, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(5, miembro.getFallecimiento());
                }
                ps.executeUpdate();
            }
        }
    }

    private static Persona persona(ResultSet rs) throws SQLException {
        int fallecimiento = rs.getInt("fallecimiento");
        Integer anioFallecimiento = rs.wasNull() ? null : fallecimiento;
        return new Persona(
                rs.getString("posicion"),
                rs.getString("nombre"),
                rs.getInt("nacimiento"),
                anioFallecimiento);
    }

    private static void rollback(Connection con) {
        try {
            con.rollback();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void restaurarAutoCommit(Connection con) {
        try {
            con.setAutoCommit(true);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void cerrar(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
