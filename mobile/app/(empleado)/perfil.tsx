import { ProfileView } from '../../components/fuel/ProfileView';

/**
 * Perfil del empleado (tab). La UI vive en ProfileView, compartida con el
 * perfil del administrador. Como es una tab no necesita botón "volver".
 */
export default function PerfilScreen() {
  return <ProfileView />;
}
