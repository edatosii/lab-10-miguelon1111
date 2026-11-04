package co.umariana.modelo;

import java.util.List;
import java.util.ArrayList;

/**
 * El árbol genealógico: un SUJETO (posición "1") con sus ASCENDIENTES (padres, abuelos... en Ahnentafel)
 * y sus DESCENDIENTES (hijos, nietos... en d'Aboville). Ver la clase Posicion.
 *
 * Son en realidad dos árboles unidos en la raíz:
 *
 *     ascendientes:  árbol BINARIO. Cada persona tiene como mucho un padre (2n) y una madre (2n + 1).
 *     descendientes: árbol GENERAL. Cada persona tiene una lista de hijos (1.1, 1.2, ...) ordenada por número.
 *
 * El árbol solo guarda a los miembros que EXISTEN: no hay "huecos" ni personas vacías. Puede faltar el padre
 * de alguien, o el hijo 1.1 aunque exista el 1.2.
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 2)
 * ------------------------------------------
 * 1. Implemente el árbol con sus PROPIOS NODOS: una clase interna "Nodo" con la Persona, el nodo del
 *    padre, el nodo de la madre y la lista de hijos. NO se permite usar Map ni Set (las pruebas lo
 *    revisan): para buscar una posición recorra el árbol siguiendo su camino
 *      - Ahnentafel: los bits de n después del primer 1 dicen el camino (0 = padre, 1 = madre).
 *        Ej.: 5 = 101 -> desde el sujeto: padre (0), luego madre (1).
 *      - D'Aboville: cada número de la posición dice a qué hijo bajar. Ej.: 1.2.3 -> hijo 2, luego su hijo 3.
 *    Se permite usar List/ArrayList para la lista de hijos.
 * 2. Todos los errores de las reglas se informan con {@link ArbolInvalidoException} (un mensaje claro).
 *    Los datos inválidos de una Persona los detecta Persona.validar() (IllegalArgumentException).
 * 3. NO modifique las pruebas (src/test).
 *
 * REGLAS DEL ÁRBOL
 *   - La posición "1" (el sujeto) existe siempre y no se puede eliminar.
 *   - Una posición no puede repetirse.
 *   - Un ascendiente n solo se puede agregar si ya existe su hijo n / 2, y debe haber NACIDO ANTES
 *     (año de nacimiento menor) que ese hijo.
 *   - Un descendiente solo se puede agregar si ya existe su progenitor, y debe haber NACIDO DESPUÉS
 *     (año de nacimiento mayor) que su progenitor.
 */
public class ArbolGenealogico {

    private static final class Nodo {
        private final Persona persona;
        private Nodo padre;
        private Nodo madre;
        private final List<Nodo> hijos = new ArrayList<>();

        private Nodo(Persona persona) {
            this.persona = persona;
        }
    }

    private final Nodo raiz;
    private int cantidad;

    /**
     * Crea un árbol que solo tiene al sujeto.
     *
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si su posición no es "1"
     */
    public ArbolGenealogico(Persona sujeto) {
        if (sujeto == null) {
            throw new IllegalArgumentException("El sujeto es obligatorio.");
        }
        sujeto.validar();
        if (!Posicion.esSujeto(sujeto.getPosicion())) {
            throw new ArbolInvalidoException("La persona inicial debe tener la posición 1.");
        }
        raiz = new Nodo(sujeto);
        cantidad = 1;
    }

    /** @return el sujeto (la persona de la posición "1") */
    public Persona sujeto() {
        return raiz.persona;
    }

    /** @return cuántos miembros tiene el árbol (el sujeto cuenta; un árbol nuevo tiene 1) */
    public int tamano() {
        return cantidad;
    }

    /**
     * @return la persona de esa posición, o null si no existe en el árbol o si la posición no es válida
     */
    public Persona buscar(String posicion) {
        Nodo nodo = localizar(posicion);
        return nodo == null ? null : nodo.persona;
    }

    /** @return true si hay alguien en esa posición (false también si la posición no es válida) */
    public boolean contiene(String posicion) {
        return localizar(posicion) != null;
    }

    /**
     * Agrega una persona en la posición que ella misma trae (la usa el constructor del árbol a partir del texto).
     * El orden de llamada importa: primero debe existir la persona de la que depende (ver REGLAS DEL ÁRBOL).
     *
     * @return la misma persona
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si la posición ya existe (o es "1"), si falta la persona de la que depende,
     *         o si los años de nacimiento no cumplen las reglas
     */
    public Persona agregar(Persona persona) {
        if (persona == null) {
            throw new IllegalArgumentException("La persona es obligatoria.");
        }
        persona.validar();
        String posicion = persona.getPosicion();
        if (localizar(posicion) != null) {
            throw new ArbolInvalidoException("Ya existe una persona en la posición " + posicion + ".");
        }

        Nodo nuevo = new Nodo(persona);
        if (Posicion.esAscendiente(posicion)) {
            Nodo hijo = localizar(Posicion.posicionDelHijoEnLinea(posicion));
            if (hijo == null) {
                throw new ArbolInvalidoException("No existe la persona de la que depende la posición " + posicion + ".");
            }
            if (persona.getNacimiento() >= hijo.persona.getNacimiento()) {
                throw new ArbolInvalidoException("Un ascendiente debe haber nacido antes que su hijo.");
            }
            if (Integer.parseInt(posicion) % 2 == 0) {
                hijo.padre = nuevo;
            } else {
                hijo.madre = nuevo;
            }
        } else if (Posicion.esDescendiente(posicion)) {
            Nodo progenitor = localizar(Posicion.posicionDelProgenitor(posicion));
            if (progenitor == null) {
                throw new ArbolInvalidoException("No existe el progenitor de la posición " + posicion + ".");
            }
            if (persona.getNacimiento() <= progenitor.persona.getNacimiento()) {
                throw new ArbolInvalidoException("Un descendiente debe haber nacido después que su progenitor.");
            }
            insertarHijoOrdenado(progenitor, nuevo);
        } else {
            throw new ArbolInvalidoException("La posición 1 ya corresponde al sujeto.");
        }
        cantidad++;
        return persona;
    }

    /**
     * Agrega el PADRE de la persona de esa posición, en la posición 2n (ver Posicion.posicionDelPadre).
     * Solo se pueden agregar padres al sujeto y a sus ascendientes.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un ascendiente, si ese padre
     *         ya existe o si no cumple las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarPadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        if (!Posicion.esAhnentafel(posicion) || localizar(posicion) == null) {
            throw new ArbolInvalidoException("No existe un ascendiente o sujeto en la posición indicada.");
        }
        return agregar(new Persona(Posicion.posicionDelPadre(posicion), nombre, nacimiento, fallecimiento));
    }

    /**
     * Igual que agregarPadre, pero agrega la MADRE (posición 2n + 1).
     */
    public Persona agregarMadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        if (!Posicion.esAhnentafel(posicion) || localizar(posicion) == null) {
            throw new ArbolInvalidoException("No existe un ascendiente o sujeto en la posición indicada.");
        }
        return agregar(new Persona(Posicion.posicionDeLaMadre(posicion), nombre, nacimiento, fallecimiento));
    }

    /**
     * Agrega un HIJO a la persona de esa posición. Solo se pueden agregar hijos al sujeto y a sus descendientes.
     * Su posición es la del progenitor + "." + el número siguiente al MAYOR número de hijo que ya exista
     * (o 1 si todavía no tiene hijos). Ej.: si "1" tiene los hijos 1.1 y 1.3, el nuevo es 1.4.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un descendiente, o si no cumple
     *         las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarHijo(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo progenitor = localizar(posicion);
        if (progenitor == null || (!Posicion.esSujeto(posicion) && !Posicion.esDescendiente(posicion))) {
            throw new ArbolInvalidoException("La posición indicada no corresponde a un sujeto o descendiente existente.");
        }
        int siguiente = 1;
        for (Nodo hijo : progenitor.hijos) {
            siguiente = Math.max(siguiente, Posicion.orden(hijo.persona.getPosicion()) + 1);
        }
        String nuevaPosicion = posicion + "." + siguiente;
        if (!Posicion.esDescendiente(nuevaPosicion)) {
            throw new ArbolInvalidoException("No se puede agregar otro hijo en esa posición.");
        }
        return agregar(new Persona(nuevaPosicion, nombre, nacimiento, fallecimiento));
    }

    /**
     * Elimina a la persona de esa posición y TODO lo que depende de ella:
     *   - si es un ascendiente: ella y todos sus propios ascendientes (sus padres, abuelos...);
     *   - si es un descendiente: ella y todos sus propios descendientes (hijos, nietos...).
     * Las demás posiciones NO cambian de número.
     *
     * @return cuántas personas se eliminaron (ella incluida)
     * @throws ArbolInvalidoException si la posición es "1" (el sujeto no se puede eliminar) o si no existe
     */
    public int eliminar(String posicion) {
        if (Posicion.esSujeto(posicion)) {
            throw new ArbolInvalidoException("No se puede eliminar al sujeto.");
        }
        Nodo nodo = localizar(posicion);
        if (nodo == null) {
            throw new ArbolInvalidoException("No existe la posición indicada.");
        }

        int eliminados;
        if (Posicion.esDescendiente(posicion)) {
            Nodo progenitor = localizar(Posicion.posicionDelProgenitor(posicion));
            progenitor.hijos.remove(nodo);
            eliminados = contarDescendientes(nodo);
        } else {
            Nodo hijoEnLinea = localizar(Posicion.posicionDelHijoEnLinea(posicion));
            if (Integer.parseInt(posicion) % 2 == 0) {
                hijoEnLinea.padre = null;
            } else {
                hijoEnLinea.madre = null;
            }
            eliminados = contarAscendientes(nodo);
        }
        cantidad -= eliminados;
        return eliminados;
    }

    /**
     * @return todos los miembros en este orden: el sujeto; luego los ascendientes por número Ahnentafel
     *         ascendente (2, 3, 4, 5...); luego los descendientes en PREORDEN (cada persona y después sus hijos:
     *         1.1, 1.1.1, 1.1.2, 1.2, ...). La lista es una copia nueva: modificarla no cambia el árbol.
     *         Este orden garantiza que cada persona aparece DESPUÉS de la que necesita (útil para guardar y cargar).
     */
    public List<Persona> miembros() {
        List<Persona> personas = new ArrayList<>();
        personas.add(raiz.persona);
        personas.addAll(ascendientes());
        agregarDescendientes(raiz, personas);
        return personas;
    }

    /** @return solo los ascendientes, por número Ahnentafel ascendente (sin el sujeto) */
    public List<Persona> ascendientes() {
        List<Persona> personas = new ArrayList<>();
        for (int posicion = 2; posicion <= Posicion.MAX_AHNENTAFEL; posicion++) {
            Nodo nodo = localizar(String.valueOf(posicion));
            if (nodo != null) {
                personas.add(nodo.persona);
            }
        }
        return personas;
    }

    /** @return solo los descendientes, en preorden (sin el sujeto) */
    public List<Persona> descendientes() {
        List<Persona> personas = new ArrayList<>();
        agregarDescendientes(raiz, personas);
        return personas;
    }

    private Nodo localizar(String posicion) {
        if (!Posicion.esValida(posicion)) {
            return null;
        }
        if (Posicion.esAhnentafel(posicion)) {
            int numero = Integer.parseInt(posicion);
            Nodo actual = raiz;
            int mascara = Integer.highestOneBit(numero) >>> 1;
            while (mascara > 0 && actual != null) {
                actual = (numero & mascara) == 0 ? actual.padre : actual.madre;
                mascara >>>= 1;
            }
            return actual;
        }

        Nodo actual = raiz;
        String[] niveles = posicion.split("\\.");
        for (int i = 1; i < niveles.length && actual != null; i++) {
            int orden = Integer.parseInt(niveles[i]);
            actual = hijoConOrden(actual, orden);
        }
        return actual;
    }

    private static Nodo hijoConOrden(Nodo progenitor, int orden) {
        for (Nodo hijo : progenitor.hijos) {
            if (Posicion.orden(hijo.persona.getPosicion()) == orden) {
                return hijo;
            }
        }
        return null;
    }

    private static void insertarHijoOrdenado(Nodo progenitor, Nodo nuevo) {
        int ordenNuevo = Posicion.orden(nuevo.persona.getPosicion());
        int indice = 0;
        while (indice < progenitor.hijos.size()
                && Posicion.orden(progenitor.hijos.get(indice).persona.getPosicion()) < ordenNuevo) {
            indice++;
        }
        progenitor.hijos.add(indice, nuevo);
    }

    private static void agregarDescendientes(Nodo nodo, List<Persona> destino) {
        for (Nodo hijo : nodo.hijos) {
            destino.add(hijo.persona);
            agregarDescendientes(hijo, destino);
        }
    }

    private static int contarDescendientes(Nodo nodo) {
        int total = 1;
        for (Nodo hijo : nodo.hijos) {
            total += contarDescendientes(hijo);
        }
        return total;
    }

    private static int contarAscendientes(Nodo nodo) {
        int total = 1;
        if (nodo.padre != null) {
            total += contarAscendientes(nodo.padre);
        }
        if (nodo.madre != null) {
            total += contarAscendientes(nodo.madre);
        }
        return total;
    }
}
