-- Línea numérica de mercados con hándicap/total (Over/Under 2.5, etc.). NULL en el resto.
ALTER TABLE opcion_cuota ADD COLUMN linea NUMERIC(5,1);
