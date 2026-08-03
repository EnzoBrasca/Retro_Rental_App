import { api } from './api';
import { Rol } from './auth';

/**
 * Listado unificado de personas (empleados + administradores) para filtros del
 * panel de administrador (ej. selector de empleado en Analítica).
 */
export interface PersonaOpcion {
  id: number;
  nombre: string;
  apellido: string;
  username: string;
  rol: Rol;
}

export function getAdminPersonas(): Promise<PersonaOpcion[]> {
  return api.get<PersonaOpcion[]>('/admin/personas');
}
