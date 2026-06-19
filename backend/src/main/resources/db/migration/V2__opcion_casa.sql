-- Casa de apuestas de la que proviene cada cuota (la mejor disponible puede variar por resultado).
ALTER TABLE opcion_cuota ADD COLUMN casa VARCHAR(60);
