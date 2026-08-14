# Tests por dominio

Los tests llevan `@Tag("<dominio>")` a nivel de clase (`ticket`, `vehiculo`, `herramienta`,
`stats`, `habilitado`, `auth`, `core`). Ver `scripts/domain-tags.conf` para el mapeo
archivo main -> dominio.

- Antes de un commit/push: `./mvnw test` (suite completa, sin flags). Es la red de
  seguridad — nunca la saltees antes de subir cambios.
- Mientras iterás una feature: `./scripts/test-affected.sh [ref-base]` corre solo los
  dominios que tocó el diff local contra `ref-base` (default `main`), incluyendo cambios
  sin commitear. Si algún archivo tocado no tiene dominio mapeado, cae automáticamente a
  la suite completa.
- Correr un dominio a mano: `./mvnw test -Dgroups=ticket` (o varios: `-Dgroups=ticket,auth`).

Un test nuevo que no lleve `@Tag` no rompe nada, pero tampoco se filtra: solo corre en
la suite completa. Si agregás una clase de test nueva, tageala.
