package co.umariana.construccion;

import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ArbolInvalidoException;
import co.umariana.modelo.Persona;
import co.umariana.modelo.Posicion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Construye un árbol genealógico a partir de un texto: cada línea es una persona.
 *
 *     posicion;nombre;nacimiento;fallecimiento
 *
 * Ejemplo (el fallecimiento puede quedar vacío):
 *
 *     1;John Doe;2002;
 *     2;Henry Doe;1920;1990
 *     3;Maria Doe;1921;
 *     1.1;Timmy Doe;2021;
 *     1.2;Amy Doe;2026;
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 3)
 * ------------------------------------------
 * Reemplace el "throw new UnsupportedOperationException" por su implementación. Esta clase usa lo que ya
 * hizo en las etapas anteriores (Posicion, Persona y ArbolGenealogico). NO modifique las pruebas (src/test).
 *
 * REGLAS
 *   1. Si el texto es null o no tiene ninguna línea con contenido: ArbolInvalidoException (mensaje: "El texto está vacío...").
 *   2. Las líneas pueden terminar en \n o en \r\n. Las líneas en blanco se ignoran, pero los números de línea
 *      que se informan cuentan TODAS las líneas del texto (la primera es la línea 1).
 *   3. Cada línea tiene 4 campos separados por ';' (el cuarto, el fallecimiento, puede estar vacío). Se acepta
 *      también la línea con solo 3 campos y sin el último ';'. Los campos se recortan (trim).
 *      Si hay menos de 3 o más de 4 campos: error.
 *   4. El nacimiento y el fallecimiento deben ser números enteros (el fallecimiento vacío es "no tiene").
 *   5. Cada persona debe ser válida (Persona.validar) y no puede haber dos líneas con la misma posición.
 *   6. Debe existir la persona "1" (el sujeto), pero puede estar en cualquier línea.
 *   7. Las líneas pueden venir en CUALQUIER orden. El constructor debe agregarlas al árbol en un orden en el
 *      que cada persona ya tenga a la que necesita (por ejemplo, primero los ascendientes por número Ahnentafel
 *      ascendente y los descendientes por generación). Las reglas del árbol (ver ArbolGenealogico) las valida el árbol.
 *   8. Todos los errores asociados a una línea se informan con ArbolInvalidoException y un mensaje que EMPIEZA con
 *      "Línea N: " (N es el número de línea de la persona con el problema), por ejemplo:
 *          "Línea 3: la posición 2 está repetida."
 *      Los errores que no son de una línea (texto vacío, falta el sujeto) no llevan ese prefijo.
 *   9. Solo se muestran los miembros que están en el texto: no se crean personas vacías para los que faltan.
 */
public final class ConstructorArbol {

    private ConstructorArbol() {
    }

    /**
     * Convierte el texto en un árbol (ver las reglas arriba).
     *
     * @throws ArbolInvalidoException si el texto no cumple las reglas
     */
    public static ArbolGenealogico construir(String texto) {
        if (texto == null) {
            throw new ArbolInvalidoException("El texto está vacío.");
        }

        String[] lineas = texto.split("\\R", -1);
        List<LineaPersona> personas = new ArrayList<>();
        Set<String> posiciones = new HashSet<>();
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            if (linea.isEmpty()) {
                continue;
            }

            int numeroLinea = i + 1;
            String[] campos = linea.split(";", -1);
            if (campos.length < 3 || campos.length > 4) {
                throw error(numeroLinea, "cada línea debe tener tres o cuatro campos.");
            }

            String posicion = campos[0].trim();
            String nombre = campos[1].trim();
            int nacimiento;
            Integer fallecimiento = null;
            try {
                nacimiento = Integer.parseInt(campos[2].trim());
                if (campos.length == 4 && !campos[3].trim().isEmpty()) {
                    fallecimiento = Integer.valueOf(campos[3].trim());
                }
            } catch (NumberFormatException e) {
                throw error(numeroLinea, "el nacimiento y el fallecimiento deben ser números enteros.");
            }

            Persona persona = new Persona(posicion, nombre, nacimiento, fallecimiento);
            try {
                persona.validar();
            } catch (IllegalArgumentException e) {
                throw error(numeroLinea, e.getMessage());
            }
            if (!posiciones.add(posicion)) {
                throw error(numeroLinea, "la posición " + posicion + " está repetida.");
            }
            personas.add(new LineaPersona(numeroLinea, persona));
        }

        if (personas.isEmpty()) {
            throw new ArbolInvalidoException("El texto está vacío.");
        }

        LineaPersona sujeto = personas.stream()
                .filter(p -> Posicion.esSujeto(p.persona().getPosicion()))
                .findFirst()
                .orElseThrow(() -> new ArbolInvalidoException("Falta la persona de posición 1 (el sujeto)."));

        personas.sort(Comparator.comparingInt((LineaPersona p) -> ordenConstruccion(p.persona()))
                .thenComparing((a, b) -> compararPosiciones(a.persona().getPosicion(), b.persona().getPosicion())));
        personas.remove(sujeto);

        ArbolGenealogico arbol = new ArbolGenealogico(sujeto.persona());
        for (LineaPersona entrada : personas) {
            try {
                arbol.agregar(entrada.persona());
            } catch (IllegalArgumentException e) {
                throw error(entrada.numeroLinea(), e.getMessage());
            }
        }
        return arbol;
    }

    private static int ordenConstruccion(Persona persona) {
        String posicion = persona.getPosicion();
        if (Posicion.esSujeto(posicion)) {
            return 0;
        }
        if (Posicion.esAscendiente(posicion)) {
            return 1;
        }
        return 2 + Posicion.generacion(posicion);
    }

    private static int compararPosiciones(String a, String b) {
        if (Posicion.esAhnentafel(a) && Posicion.esAhnentafel(b)) {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        }
        if (Posicion.esDescendiente(a) && Posicion.esDescendiente(b)) {
            String[] nivelesA = a.split("\\.");
            String[] nivelesB = b.split("\\.");
            for (int i = 1; i < Math.min(nivelesA.length, nivelesB.length); i++) {
                int comparacion = Integer.compare(Integer.parseInt(nivelesA[i]), Integer.parseInt(nivelesB[i]));
                if (comparacion != 0) {
                    return comparacion;
                }
            }
            return Integer.compare(nivelesA.length, nivelesB.length);
        }
        return 0;
    }

    private static ArbolInvalidoException error(int linea, String mensaje) {
        return new ArbolInvalidoException("Línea " + linea + ": " + mensaje);
    }

    private record LineaPersona(int numeroLinea, Persona persona) {
    }
}
