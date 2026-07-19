// Closes the modal on client-side navigation to any other route: parallel-route
// slots keep their last active content on soft nav, so matching everything else
// to a null page ensures the overlay dismisses. See Next.js parallel-routes docs.
export default function ModalCatchAll() {
  return null;
}
