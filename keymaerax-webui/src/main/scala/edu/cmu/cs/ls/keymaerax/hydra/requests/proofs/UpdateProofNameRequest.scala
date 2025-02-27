/**
 * Copyright (c) Carnegie Mellon University.
 * See LICENSE.txt for the conditions of this license.
 */
package edu.cmu.cs.ls.keymaerax.hydra.requests.proofs

import edu.cmu.cs.ls.keymaerax.hydra.responses.proofs.UpdateProofNameResponse
import edu.cmu.cs.ls.keymaerax.hydra.{DBAbstraction, Response, UserProofRequest, WriteRequest}

import scala.collection.immutable.{List, Nil}

class UpdateProofNameRequest(db: DBAbstraction, userId: String, proofId: String, newName: String)
  extends UserProofRequest(db, userId, proofId) with WriteRequest {
  override protected def doResultingResponses(): List[Response] = {
    db.updateProofName(proofId, newName)
    new UpdateProofNameResponse(proofId, newName) :: Nil
  }
}
