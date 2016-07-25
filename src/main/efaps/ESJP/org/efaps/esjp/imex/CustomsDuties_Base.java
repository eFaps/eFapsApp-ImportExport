/*
 * Copyright 2003 - 2012 The eFaps Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Revision:        $Rev: 8342 $
 * Last Changed:    $Date: 2012-12-11 09:42:17 -0500 (Tue, 11 Dec 2012) $
 * Last Changed By: $Author: jan@moxter.net $
 */


package org.efaps.esjp.imex;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Insert;
import org.efaps.esjp.ci.CIFormImEx;
import org.efaps.esjp.ci.CIImEx;
import org.efaps.esjp.sales.document.AbstractDocumentSum;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: Import_Base.java 10301 2013-09-24 21:23:03Z jan@moxter.net $
 */
@EFapsUUID("be9290b7-4ab4-42b6-9db2-8ab4530ada2e")
@EFapsApplication("eFapsApp-ImportExport")
public abstract class CustomsDuties_Base
    extends AbstractDocumentSum
{

    /**
     * Method for create a new Quotation.
     *
     * @param _parameter Parameter as passed from eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    @SuppressWarnings("unused")
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final CreatedDoc createdDoc = createDoc(_parameter);
        return new Return();
    }

    @Override
    protected void add2DocCreate(final Parameter _parameter,
                                 final Insert _insert,
                                 final CreatedDoc _createdDoc)
        throws EFapsException
    {
        _insert.add(CIImEx.CustomsDuties.AduanasAgency,
                        _parameter.getParameterValue(CIFormImEx.ImEx_CustomsDutiesForm.aduanasAgency.name));
        _insert.add(CIImEx.CustomsDuties.Bank,
                        _parameter.getParameterValue(CIFormImEx.ImEx_CustomsDutiesForm.bank.name));
        _insert.add(CIImEx.CustomsDuties.Principal,
                        _parameter.getParameterValue(CIFormImEx.ImEx_CustomsDutiesForm.principal.name));
        _insert.add(CIImEx.CustomsDuties.Storage,
                        _parameter.getParameterValue(CIFormImEx.ImEx_CustomsDutiesForm.storage.name));
        _insert.add(CIImEx.CustomsDuties.QuantityPackages,
                        _parameter.getParameterValue(CIFormImEx.ImEx_CustomsDutiesForm.quantityPackages.name));
        _insert.add(CIImEx.CustomsDuties.GrossWeight,
                        _parameter.getParameterValue(CIFormImEx.ImEx_CustomsDutiesForm.grossWeight.name));
        _insert.add(CIImEx.CustomsDuties.GoodsIssueDate,
                        _parameter.getParameterValue(CIFormImEx.ImEx_CustomsDutiesForm.goodsIssueDate.name));
    }
}
