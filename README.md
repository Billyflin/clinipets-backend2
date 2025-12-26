```bash 
repomix --ignore "**/build/**,**/target/**,**/.gradle/**,**/.idea/**,**/.git/**,**/*.jar,**/*.class,**/mvnw*,firebase-service-account.json" --output repomix-backendversionhf.xml   
```

## Sistema de Agendamiento - Estados de Cita

El sistema utiliza una máquina de estados simplificada para gestionar el flujo de atención:

### Estados
- **CONFIRMADA**: La cita ha sido reservada (aún no pagada).
- **EN_ATENCION**: El paciente está siendo atendido por el veterinario.
- **FINALIZADA**: La atención concluyó, se procesó el pago y se descontó el stock.
- **CANCELADA**: La cita fue cancelada y el stock (si se hubiera consumido) fue devuelto.
- **NO_ASISTIO**: El paciente no se presentó (No Show).

### Flujo Principal
`CONFIRMADA` → `EN_ATENCION` → `FINALIZADA`

### Otros Flujos
- `CONFIRMADA` → `CANCELADA` (Cancelación previa)
- `CONFIRMADA` → `NO_ASISTIO` (Limpieza automática tras expiración)
- `EN_ATENCION` → `CANCELADA` (Si falla el pago o el consumo de stock al finalizar)

### Reglas de Negocio
- **Reserva**: Se valida disponibilidad de stock pero NO se descuenta.
- **Finalización**: Se intenta descontar stock con lock pesimista. Si falla, la cita pasa a `CANCELADA` automáticamente.
- **Limpieza**: Citas `CONFIRMADA` expiradas pasan a `NO_ASISTIO`.
