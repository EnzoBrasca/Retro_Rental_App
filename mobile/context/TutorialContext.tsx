import { createContext, useContext, useState, useCallback, useMemo, ReactNode } from 'react';
import { TUTORIAL_STEPS } from '../constants/tutorial';
import { TutorialOverlay } from '../components/fuel/TutorialOverlay';

/**
 * Contexto del tutorial de onboarding del área de empleado.
 *
 * Reemplaza al que en el prototipo vivía dentro de `EmployeeArea` (React
 * Navigation). Acá el provider mantiene el estado (visible + paso), renderiza
 * el <TutorialOverlay> por encima de las tabs y expone `open()` para que
 * cualquier pantalla (Flota, Perfil) dispare el tutorial con el botón "?".
 */
type TutorialContextType = {
  open: () => void;
};

const TutorialContext = createContext<TutorialContextType>({ open: () => {} });

export const useTutorial = () => useContext(TutorialContext);

export function TutorialProvider({ children }: { children: ReactNode }) {
  const [visible, setVisible] = useState(false);
  const [step, setStep] = useState(0);

  const handleNext = () => {
    if (step === TUTORIAL_STEPS.length - 1) {
      setVisible(false);
    } else {
      setStep((s) => s + 1);
    }
  };

  // Igual que en AuthContext: un objeto literal cambia de identidad en cada
  // render y re-renderiza a todo consumidor de useTutorial().
  const open = useCallback(() => {
    setStep(0);
    setVisible(true);
  }, []);
  const value = useMemo(() => ({ open }), [open]);

  return (
    <TutorialContext.Provider value={value}>
      {children}
      <TutorialOverlay
        visible={visible}
        step={step}
        onNext={handleNext}
        onClose={() => setVisible(false)}
      />
    </TutorialContext.Provider>
  );
}
