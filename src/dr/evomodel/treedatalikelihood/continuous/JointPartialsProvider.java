package dr.evomodel.treedatalikelihood.continuous;

import dr.evolution.tree.Tree;
import dr.evomodel.treedatalikelihood.continuous.cdi.PrecisionType;
import dr.evomodel.treedatalikelihood.preorder.WrappedNormalSufficientStatistics;
import dr.inference.model.*;
import dr.math.matrixAlgebra.WrappedMatrix;
import dr.math.matrixAlgebra.WrappedVector;
import dr.math.matrixAlgebra.missingData.MissingOps;
import dr.xml.*;
import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;

import java.util.List;

import static dr.math.matrixAlgebra.WrappedMatrix.Utils.transferSymmetricBlockDiagonal;
import static dr.math.matrixAlgebra.WrappedMatrix.Utils.wrapBlockDiagonalMatrix;

/**
 * @author Gabriel Hassler
 * @author Marc A. Suchard
 */

public class JointPartialsProvider extends AbstractModel implements ContinuousTraitPartialsProvider {

    private final String name;
    private final ContinuousTraitPartialsProvider[] providers;
    private final int traitDim;
    private final int dataDim;

    private final List<Integer> missingIndices;
    private final boolean[] missingIndicators;

    private final boolean defaultAllowSingular;
    private final Boolean computeDeterminant; // TODO: Maybe pass as argument?

    private static final PrecisionType precisionType = PrecisionType.FULL; //TODO: base on child precisionTypes (make sure they're all the same)

    private String tipTraitName;

    private final CompoundParameter jointDataParameter;

    private static final Boolean DEBUG = false;

    public JointPartialsProvider(String name, ContinuousTraitPartialsProvider[] providers) {
        super(name);
        this.name = name;
        this.providers = providers;

        int traitDim = 0;
        int dataDim = 0;
        for (ContinuousTraitPartialsProvider provider : providers) {
            traitDim += provider.getTraitDimension();
            dataDim += provider.getDataDimension();
        }

        this.traitDim = traitDim;
        this.dataDim = dataDim;

        this.missingIndicators = setupMissingIndicators();
        this.missingIndices = ContinuousTraitPartialsProvider.indicatorToIndices(missingIndicators);

        this.defaultAllowSingular = setDefaultAllowSingular();
        this.computeDeterminant = defaultAllowSingular; // TODO: not perfect behavior, should be based on actual value of `allowSingular`

        for (ContinuousTraitPartialsProvider provider : providers) {
            if (provider instanceof Model) {
                addModel((Model) provider);
            }
        }

        CompoundParameter[] parameters = new CompoundParameter[providers.length];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = providers[i].getParameter();
        }

        this.jointDataParameter = CompoundParameter.mergeParameters(parameters);
        if (DEBUG) {
            CompoundParameter.checkParametersMerged(jointDataParameter, parameters);
        }
    }


    private boolean[] setupMissingIndicators() {
        int nTaxa = providers[0].getParameter().getParameterCount();
        boolean[] indicators = new boolean[dataDim * nTaxa];
        boolean[][] subIndicators = new boolean[providers.length][0];
        for (int i = 0; i < providers.length; i++) {
            subIndicators[i] = providers[i].getDataMissingIndicators();
        }
        for (int taxonI = 0; taxonI < nTaxa; taxonI++) {
            int offset = taxonI * dataDim;

            for (int providerI = 0; providerI < providers.length; providerI++) {
                int srcDim = providers[providerI].getDataDimension();
                int srcOffset = taxonI * srcDim;
                System.arraycopy(subIndicators[providerI], srcOffset, indicators, offset, srcDim);
                offset += srcDim;
            }
        }

        return indicators;
    }


    @Override
    public boolean bufferTips() {
        return true; //TODO: not sure what this does, but it's set to `true` for every implementation
    }

    @Override
    public int getTraitCount() {
        return providers[0].getTraitCount();
    }

    @Override
    public int getTraitDimension() {
        return traitDim;
    }

    @Override
    public String getTipTraitName() {
        return tipTraitName;
    }

    @Override
    public void setTipTraitName(String name) {
        tipTraitName = name;
        for (int i = 0; i < providers.length; i++) {
            providers[i].setTipTraitName(name + "." + i); // TODO: make static method for making name both here and in PartitionedTreeTraitProvider;
        }
    }

    @Override
    public int getDataDimension() {
        return dataDim; //TODO: maybe throw error here? Used for model extension, mse stuff and it might be worth putting conditions if JointPartialsProvider
    }

    @Override
    public int[] getPartitionDimensions() {
        int[] dims = new int[providers.length];
        for (int i = 0; i < providers.length; i++) {
            dims[i] = providers[i].getTraitDimension();
        }
        return dims;
    }

    @Override
    public PrecisionType getPrecisionType() {
        return precisionType;
    }

    @Override
    public double[] getTipPartial(int taxonIndex, boolean fullyObserved) {
        double[] partial = new double[precisionType.getPartialsDimension(traitDim)];

        int meanOffset = precisionType.getMeanOffset(traitDim);
        int precOffset = precisionType.getPrecisionOffset(traitDim);
        int varOffset = precisionType.getVarianceOffset(traitDim);
        int effDimDim = precisionType.getEffectiveDimensionOffset(traitDim);
        int detDim = precisionType.getDeterminantOffset(traitDim);

        WrappedMatrix.Indexed precWrap = wrapBlockDiagonalMatrix(partial, precOffset, 0, traitDim); //TODO: this only works for precisionType.FULL, make general
        WrappedMatrix.Indexed varWrap = wrapBlockDiagonalMatrix(partial, varOffset, 0, traitDim); //TODO: see above

        int currentMatrixOffset = 0;


        for (ContinuousTraitPartialsProvider provider : providers) {
            double[] subPartial = provider.getTipPartial(taxonIndex, fullyObserved);
            int subDim = provider.getTraitDimension();

            int precisionOffset = precisionType.getPrecisionOffset(subDim);

            WrappedMatrix.Raw subPrec = new WrappedMatrix.Raw(subPartial, precisionOffset, subDim, subDim); //TODO: see above
            transferSymmetricBlockDiagonal(subPrec, precWrap, currentMatrixOffset); //TODO: see above

            WrappedMatrix.Raw subVar = new WrappedMatrix.Raw(subPartial, precisionType.getVarianceOffset(subDim), subDim, subDim); //TODO: see above
            transferSymmetricBlockDiagonal(subVar, varWrap, currentMatrixOffset); //TODO: see above

            currentMatrixOffset += subDim;


            System.arraycopy(subPartial, precisionType.getMeanOffset(subDim), partial, meanOffset, subDim);
            meanOffset += subDim;

            if (precisionType.hasEffectiveDimension()) {
                partial[effDimDim] += subPartial[precisionType.getEffectiveDimensionOffset(subDim)];
            }

            if (precisionType.hasEffectiveDimension() && computeDeterminant) {

                double subDet = subPartial[precisionType.getDeterminantOffset(subDim)];

                if (!precisionType.isMissingDeterminantValue(subDet)) {
                    //TODO: what was I trying to do here?
//                    DenseMatrix64F prec = MissingOps.wrap(subPartial, precisionOffset, subDim, subDim);
//                    DenseMatrix64F var = new DenseMatrix64F(subDim, subDim);
//                    subDet = MissingOps.safeInvert2(prec, var, true).getLogDeterminant();
                }

                partial[detDim] += subDet;
            }
        }

        if (!computeDeterminant) {
            precisionType.fillNoDeterminantInPartials(partial, 0, traitDim);
        }

        //Assume conditional independence (for now)
        return partial;
    }


    @Override
    public List<Integer> getMissingIndices() {
        return missingIndices;
    }

    @Override
    public boolean[] getDataMissingIndicators() {
        return missingIndicators;
    }

    @Override
    public CompoundParameter getParameter() {
        return jointDataParameter;
    }

    @Override
    protected void handleModelChangedEvent(Model model, Object object, int index) {
        fireModelChanged(); // sub-providers should handle everything else
    }

    @Override
    protected void handleVariableChangedEvent(Variable variable, int index, Parameter.ChangeType type) {
        // no variables
    }

    @Override
    protected void storeState() {
        // nothing to store
    }

    @Override
    protected void restoreState() {
        // nothing to restore
    }

    @Override
    protected void acceptState() {
        // nothing to do
    }

    @Override
    public boolean getDefaultAllowSingular() {
        return defaultAllowSingular;
    }

    private boolean setDefaultAllowSingular() {
        boolean allowSingular = false;
        for (ContinuousTraitPartialsProvider provider : providers) {
            allowSingular = allowSingular || provider.getDefaultAllowSingular();
        }
        return allowSingular;
    }

    @Override
    public boolean suppliesWishartStatistics() {
        boolean suppliesStatistics = true;
        for (ContinuousTraitPartialsProvider provider : providers) {
            suppliesStatistics = suppliesStatistics && provider.suppliesWishartStatistics();
        }
        return suppliesStatistics;
    }

    @Override
    public void addTreeAndRateModel(Tree treeModel, ContinuousRateTransformation rateTransformation) {
        for (ContinuousTraitPartialsProvider provider : providers) {
            provider.addTreeAndRateModel(treeModel, rateTransformation);
        }
    }

    @Override
    public WrappedNormalSufficientStatistics partitionNormalStatistics(WrappedNormalSufficientStatistics statistic,
                                                                       ContinuousTraitPartialsProvider provider) {

        int traitOffset = 0;
        for (ContinuousTraitPartialsProvider potentialProvider : providers) {
            if (provider == potentialProvider) {
                break;
            } else {
                traitOffset += potentialProvider.getTraitDimension();
            }
        }

        int traitDim = provider.getTraitDimension();

        WrappedVector originalMean = statistic.getMean();
        WrappedVector newMean = new WrappedVector.View(originalMean, traitOffset, traitDim);

        int[] varianceIndices = new int[traitDim];
        for (int i = 0; i < traitDim; i++) {
            varianceIndices[i] = i + traitOffset;
        }

        WrappedMatrix originalVariance = statistic.getVariance();
        DenseMatrix64F newVariance = new DenseMatrix64F(traitDim, traitDim);

        for (int i = 0; i < traitDim; i++) {
            for (int j = 0; j < traitDim; j++) {
                newVariance.set(i, j, originalVariance.get(varianceIndices[i], varianceIndices[j]));
            }
        }

        DenseMatrix64F newPrecision = new DenseMatrix64F(traitDim, traitDim);
        CommonOps.invert(newVariance, newPrecision); //TODO: cholesky

        return new WrappedNormalSufficientStatistics(newMean, new WrappedMatrix.WrappedDenseMatrix(newPrecision),
                new WrappedMatrix.WrappedDenseMatrix(newVariance));
    }


    public static final AbstractXMLObjectParser PARSER = new AbstractXMLObjectParser() {
        private static final String PARSER_NAME = "jointPartialsProvider";


        @Override
        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            List<ContinuousTraitPartialsProvider> providersList =
                    xo.getAllChildren(ContinuousTraitPartialsProvider.class);

            ContinuousTraitPartialsProvider[] providers = new ContinuousTraitPartialsProvider[providersList.size()];

            for (int i = 0; i < providersList.size(); i++) {
                providers[i] = providersList.get(i);
            }

            int traitCount = providers[0].getTraitCount();
            for (int i = 1; i < providers.length; i++) {
                if (providers[i].getTraitCount() != traitCount) {
                    throw new XMLParseException("all partials providers must have the same trait count");
                }

            }
            return new JointPartialsProvider(PARSER_NAME, providers);
        }

        @Override
        public XMLSyntaxRule[] getSyntaxRules() {
            return new XMLSyntaxRule[]{
                    new ElementRule(ContinuousTraitPartialsProvider.class, 0, Integer.MAX_VALUE)
            };
        }

        @Override
        public String getParserDescription() {
            return "Merges two Gaussian processes.";
        }

        @Override
        public Class getReturnType() {
            return JointPartialsProvider.class;
        }

        @Override
        public String getParserName() {
            return PARSER_NAME;
        }
    };


}
